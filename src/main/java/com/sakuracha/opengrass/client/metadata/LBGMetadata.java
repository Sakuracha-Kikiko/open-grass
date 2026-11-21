package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a better grass metadata, which holds the layers used to render a block.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGMetadata {
    public final ResourceLocation id;
    private final List<LBGLayer> layers = new ArrayList<>();
    private int lastLayerIndex;
    private @Nullable Consumer<BakedModel> snowyModelVariantProvider;
    private @Nullable BakedModel snowyModelVariant;

    public LBGMetadata(ResourceManager resourceManager, ResourceLocation id, JsonObject json) {
        this.id = id;

        if (json.has("layers")) {
            var builtLayers = new ArrayList<LBGLayer>();
            for (var element : json.getAsJsonArray("layers")) {
                builtLayers.add(new LBGLayer(this, element.getAsJsonObject()));
            }

            for (var layer : builtLayers) {
                layer.buildTextures(resourceManager);
            }

            var parentLayers = new LinkedHashMap<Integer, LBGLayer>();
            for (var layer : builtLayers) {
                var parent = parentLayers.get(layer.colorIndex());
                if (parent == null) {
                    parentLayers.put(layer.colorIndex(), layer);
                } else {
                    LBGLayer.mergeLayers(parent, layer);
                }
            }

            this.layers.addAll(parentLayers.values());
        }
    }

    public int nextLayerIndex() {
        return this.lastLayerIndex++;
    }

    public void bakeTextures(java.util.function.Function<Material, TextureAtlasSprite> spriteGetter) {
        for (var layer : this.layers) {
            layer.bakeTextures(spriteGetter);
        }
    }

    public Optional<LBGLayer> getLayer(int colorIndex) {
        for (var layer : this.layers) {
            if (layer.colorIndex() == colorIndex) {
                return Optional.of(layer);
            }
        }

        return Optional.empty();
    }

    public @Nullable BakedModel getSnowyModelVariant() {
        return this.snowyModelVariant;
    }

    public void setSnowyModelVariant(@Nullable BakedModel snowyModelVariant) {
        this.snowyModelVariant = snowyModelVariant;
    }

    public void setSnowyModelVariantProvider(@Nullable Consumer<BakedModel> snowyModelVariantProvider) {
        this.snowyModelVariantProvider = snowyModelVariantProvider;
    }

    public void propagate(BakedModel bakedModel) {
        if (this.snowyModelVariantProvider != null) {
            this.snowyModelVariantProvider.accept(bakedModel);
        }
    }
}
