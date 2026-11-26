package com.sakuracha.opengrass.client.model;

import com.sakuracha.opengrass.Config;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.metadata.LBGCompiledLayerMetadata;
import com.sakuracha.opengrass.client.util.LayeredBlockUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Renders an extra layer over a block, such as snow over a grass block.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGLayerBakedModel extends BakedModelWrapper<BakedModel> {
    private final List<LBGCompiledLayerMetadata> metadatas;

    public LBGLayerBakedModel(BakedModel originalModel, List<LBGCompiledLayerMetadata> metadatas) {
        super(originalModel);
        this.metadatas = List.copyOf(metadatas);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        // The layer models need the world and position to figure out whether a layer should be drawn,
        // and this wrapper can be the outermost one for layer states (such as fences or carpets).
        return super.getModelData(level, pos, state, data).derive()
                .with(LBGData.BLOCK_CONTEXT, new LBGData.BlockContext(level, pos))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        var base = super.getQuads(state, side, rand, data, renderType);
        if (state == null || !Config.betterSnow()) {
            return base;
        }

        var context = data.get(LBGData.BLOCK_CONTEXT);
        if (context == null) {
            return base;
        }

        var level = context.level();
        var pos = context.pos();

        for (var metadata : this.metadatas) {
            if (LayeredBlockUtils.getNearbyLayeredBlocks(level, pos, metadata.layerType(), state.getBlock(), false) <= 1) {
                continue;
            }

            var layerModel = metadata.layerModel();
            var alternateModel = metadata.alternateModel();
            boolean layer = layerModel != null && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);

            if (!layer && alternateModel == null) {
                continue;
            }

            var result = new ArrayList<BakedQuad>();
            if (layer) {
                // The layer is the model of a block state of the game, so it has to be queried with its own state.
                // A null render type is passed on purpose: the layer is emitted in the render types of the block it
                // covers, while models such as multipart ones filter their quads on the render type they support.
                result.addAll(LBGQuadUtil.prepareLayerQuads(
                        layerModel.getQuads(metadata.layerType().displayState(), side, rand, ModelData.EMPTY, null),
                        state.getOffset(level, pos)));
            }

            if (alternateModel != null) {
                result.addAll(alternateModel.getQuads(state, side, rand, data, renderType));
            } else {
                result.addAll(LBGQuadUtil.translate(base, metadata.offset()));
            }

            return result;
        }

        return base;
    }
}
