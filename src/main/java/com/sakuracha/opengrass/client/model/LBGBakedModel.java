package com.sakuracha.opengrass.client.model;

import com.sakuracha.opengrass.Config;
import com.sakuracha.opengrass.LBGMode;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.metadata.LBGMetadata;
import com.sakuracha.opengrass.client.util.LayeredBlockUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the connected side textures of a block, and the optional snowy variant of a grass block.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGBakedModel extends BakedModelWrapper<BakedModel> {
    private final LBGMetadata metadata;

    public LBGBakedModel(BakedModel originalModel, LBGMetadata metadata) {
        super(originalModel);
        this.metadata = metadata;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return super.getModelData(level, pos, state, data).derive()
                .with(LBGData.BLOCK_CONTEXT, new LBGData.BlockContext(level, pos))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        if (state == null) {
            return super.getQuads(state, side, rand, data, renderType);
        }

        var mode = Config.mode();
        if (!mode.isEnabled()) {
            return super.getQuads(state, side, rand, data, renderType);
        }

        var context = data.get(LBGData.BLOCK_CONTEXT);
        if (context == null) {
            return super.getQuads(state, side, rand, data, renderType);
        }

        var snowyModel = this.metadata.getSnowyModelVariant();
        if (snowyModel != null && Config.betterSnow()
                && state.hasProperty(BlockStateProperties.SNOWY)
                && !state.getValue(BlockStateProperties.SNOWY)) {
            var level = context.level();
            var pos = context.pos();
            var up = level.getBlockState(pos.above());

            if (!up.isAir() && LayeredBlockUtils.shouldGrassBeSnowy(level, pos,
                    BuiltInRegistries.BLOCK.getKey(up.getBlock()), up, false)) {
                return snowyModel.getQuads(state.setValue(BlockStateProperties.SNOWY, true), side, rand, data, renderType);
            }
        }

        var base = super.getQuads(state, side, rand, data, renderType);
        if (base.isEmpty()) {
            return base;
        }

        var result = new ArrayList<BakedQuad>(base.size());
        for (var quad : base) {
            result.add(this.editQuad(quad, state, context, mode));
        }
        return result;
    }

    private BakedQuad editQuad(BakedQuad quad, BlockState state, LBGData.BlockContext context, LBGMode mode) {
        if (!LayeredBlockUtils.canEditQuad(quad)) {
            return quad;
        }

        var layer = this.metadata.getLayer(quad.getTintIndex()).orElse(null);
        if (layer == null) {
            return quad;
        }

        var level = context.level();
        var pos = context.pos();

        if (mode == LBGMode.FASTEST) {
            return spriteBake(quad, layer, "connect");
        }

        var face = quad.getDirection();
        if (face == null) {
            return quad;
        }

        var right = face.getClockWise();
        var left = face.getCounterClockWise();

        if (LayeredBlockUtils.canFullyConnect(level, state, pos, face)) {
            var connected = spriteBake(quad, layer, "connect");
            if (connected != quad) {
                return connected;
            }
        }

        if (mode != LBGMode.FANCY) {
            return quad;
        }

        boolean rightMatch = LayeredBlockUtils.canConnect(level, state, pos.below(), right)
                || (LayeredBlockUtils.canConnect(level, state, pos, right)
                        && LayeredBlockUtils.canFullyConnect(level, state, pos.relative(right), face));
        boolean leftMatch = LayeredBlockUtils.canConnect(level, state, pos.below(), left)
                || (LayeredBlockUtils.canConnect(level, state, pos, left)
                        && LayeredBlockUtils.canFullyConnect(level, state, pos.relative(left), face));

        if (rightMatch && leftMatch) {
            return spriteBake(quad, layer, "arch");
        } else if (rightMatch) {
            return spriteBake(quad, layer, "blend_up_m");
        } else if (leftMatch) {
            return spriteBake(quad, layer, "blend_up");
        }

        return quad;
    }

    private static BakedQuad spriteBake(BakedQuad quad, com.sakuracha.opengrass.client.metadata.LBGLayer layer, String kind) {
        return LBGQuadUtil.withSprite(quad, layer.getBakedTexture(kind));
    }
}
