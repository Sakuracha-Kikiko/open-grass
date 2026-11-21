package com.sakuracha.opengrass.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

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
    public ChunkRenderTypeSet getRenderTypes(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.util.RandomSource rand, net.neoforged.neoforge.client.model.data.ModelData data) {
        return this.renderTypes;
    }
}
