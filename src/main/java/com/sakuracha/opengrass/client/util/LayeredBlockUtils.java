package com.sakuracha.opengrass.client.util;

import com.sakuracha.opengrass.Config;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.metadata.LBGLayerState;
import java.util.List;
import java.util.function.DoublePredicate;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Utilities to check whether blocks should connect to each other, and whether a block is covered by a layer.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LayeredBlockUtils {
    private static final List<Direction> HORIZONTAL_DIRECTIONS = List.of(Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST);

    private LayeredBlockUtils() {
    }

    /**
     * Checks whether a block should be considered snowy, based on the blocks surrounding the block above it.
     *
     * @param level the level
     * @param pos the position of the block below the layer
     * @param stateId the identifier of the state of the layer block
     * @param upState the block state of the layer block
     * @param onlyPureSnow whether only pure snow blocks should be considered
     * @return {@code true} if the block should be considered snowy
     */
    public static boolean shouldGrassBeSnowy(BlockAndTintGetter level, BlockPos pos, ResourceLocation stateId,
            BlockState upState, boolean onlyPureSnow) {
        if (upState.getRenderShape() != RenderShape.MODEL) {
            return false;
        }

        if (!(LBGData.state(stateId) instanceof LBGLayerState layerState)) {
            return false;
        }

        var variant = LBGData.variantOf(upState);
        boolean[] shouldBeSnowy = {false};
        layerState.forEach(variant, metadata -> {
            if (metadata.layerType().getName().equals("snow") && metadata.hasLayerModel()) {
                shouldBeSnowy[0] = true;
            }
        });

        if (!shouldBeSnowy[0]) {
            return false;
        }

        return getNearbySnowyBlocks(level, pos.above(), upState.getBlock(), onlyPureSnow) > 1;
    }

    public static int getNearbySnowyBlocks(BlockAndTintGetter level, BlockPos pos, Block type, boolean onlyPureSnow) {
        return getNearbyLayeredBlocks(level, pos, Blocks.SNOW, type, onlyPureSnow);
    }

    /**
     * Counts the blocks of the given type around the given position which are covered by the given layer block.
     *
     * @param level the level
     * @param pos the position
     * @param layerBlock the layer block
     * @param type the type of the blocks to check
     * @param onlySourceBlock whether only the layer block should be considered
     * @return the amount of nearby layered blocks
     */
    public static int getNearbyLayeredBlocks(BlockAndTintGetter level, BlockPos pos, Block layerBlock, Block type,
            boolean onlySourceBlock) {
        int count = 0;

        for (var direction : HORIZONTAL_DIRECTIONS) {
            var offsetPos = pos.relative(direction);
            var block = level.getBlockState(offsetPos).getBlock();

            if (block == type && !onlySourceBlock) {
                if (getNearbyBlockLayers(level, offsetPos, layerBlock) > 1) {
                    count++;
                }
            } else if (block == layerBlock) {
                count++;
            }
        }

        return count;
    }

    private static int getNearbyBlockLayers(BlockAndTintGetter level, BlockPos pos, Block layerBlock) {
        int count = 0;

        for (var direction : HORIZONTAL_DIRECTIONS) {
            if (level.getBlockState(pos.relative(direction)).getBlock() == layerBlock) {
                count++;
            }
        }

        return count;
    }

    public static boolean canConnect(BlockAndTintGetter level, BlockState self, BlockPos selfPos, Direction direction) {
        return canConnect(level, self, selfPos, selfPos.relative(direction));
    }

    public static boolean canFullyConnect(BlockAndTintGetter level, BlockState self, BlockPos selfPos, Direction direction) {
        return canConnect(level, self, selfPos, selfPos.relative(direction).below());
    }

    /**
     * Checks whether the given block state can connect to the block at the given position.
     *
     * @param level the level
     * @param self the block state
     * @param selfPos the position of the block
     * @param adjacentPos the position of the adjacent block
     * @return {@code true} if the block can connect to the adjacent block
     */
    private static boolean canConnect(BlockAndTintGetter level, BlockState self, BlockPos selfPos, BlockPos adjacentPos) {
        var adjacent = level.getBlockState(adjacentPos);
        var upPos = adjacentPos.above();
        var up = level.getBlockState(upPos);

        if (Config.betterSnow() && self.getBlock() instanceof SnowyDirtBlock) {
            boolean selfSnowy = self.getValue(BlockStateProperties.SNOWY);

            if (selfSnowy && !up.isAir()) {
                if (up.is(Blocks.SNOW)) {
                    return true;
                } else if (adjacent.getBlock() instanceof SnowyDirtBlock) {
                    // Faithful to the original implementation: the state identifier is built with the states path
                    // prefix, which never matches a loaded state as those are indexed by block identifier only.
                    var blockId = BuiltInRegistries.BLOCK.getKey(up.getBlock());
                    var stateId = ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(),
                            com.sakuracha.opengrass.client.metadata.LBGState.PATH_PREFIX + "/" + blockId.getPath());
                    return shouldGrassBeSnowy(level, adjacentPos, stateId, up, true);
                }
            }
        }

        return self == adjacent && (up.isAir() || !up.isFaceSturdy(level, upPos, Direction.DOWN));
    }

    /**
     * Checks whether the given quad can be edited, which means it is a side face which covers the whole edge of a block.
     *
     * @param quad the quad
     * @return {@code true} if the quad can be edited
     */
    public static boolean canEditQuad(BakedQuad quad) {
        var face = quad.getDirection();
        if (face == null || face.getAxis() == Direction.Axis.Y) {
            return false;
        }

        if (testAllVertices(quad, 1, value -> value > 1.0F || value < 0.0F)) {
            return false;
        }

        var axis = face.getAxis() == Direction.Axis.X ? 2 : 0;
        return !testAllVertices(quad, axis, value -> value != 0.0F)
                || !testAllVertices(quad, axis, value -> value != 1.0F);
    }

    private static boolean testAllVertices(BakedQuad quad, int axis, DoublePredicate predicate) {
        var vertices = quad.getVertices();
        var stride = vertices.length / 4;

        for (int i = 0; i < 4; i++) {
            float value = Float.intBitsToFloat(vertices[i * stride + axis]);
            if (!predicate.test(value)) {
                return false;
            }
        }

        return true;
    }
}
