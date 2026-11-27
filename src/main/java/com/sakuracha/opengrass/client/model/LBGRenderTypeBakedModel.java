package com.sakuracha.opengrass.client.model;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Forces the render type of a model, used for the blocks which are rendered as a layer over another block.
 *
 * @author Sakuracha Kikiko
 */
public final class LBGRenderTypeBakedModel extends BakedModelWrapper<BakedModel> {
    private final ChunkRenderTypeSet renderTypes;

    public LBGRenderTypeBakedModel(BakedModel originalModel, RenderType renderType) {
        super(originalModel);
        this.renderTypes = ChunkRenderTypeSet.of(renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.renderTypes;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
            @Nullable RenderType renderType) {
        // The model is rendered in a single render type, so every quad has to be returned for it: the models
        // which split their quads per render type (multipart models for example) would drop them otherwise.
        return super.getQuads(state, side, rand, data, null);
    }
}
