package com.sakuracha.opengrass.client.metadata;

import net.minecraft.client.resources.model.BakedModel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Represents a compiled layer metadata, which holds the baked models to render when the layer variation should be used.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGCompiledLayerMetadata {
    private final LBGLayerType layerType;
    private final @Nullable Vector3f offset;
    private final @Nullable BakedModel layerModel;
    private final @Nullable BakedModel alternateModel;

    public LBGCompiledLayerMetadata(LBGLayerType layerType, @Nullable Vector3f offset, @Nullable BakedModel layerModel,
            @Nullable BakedModel alternateModel) {
        this.layerType = layerType;
        this.offset = offset;
        this.layerModel = layerModel;
        this.alternateModel = alternateModel;
    }

    public LBGLayerType layerType() {
        return this.layerType;
    }

    public @Nullable Vector3f offset() {
        return this.offset;
    }

    public @Nullable BakedModel layerModel() {
        return this.layerModel;
    }

    public @Nullable BakedModel alternateModel() {
        return this.alternateModel;
    }
}
