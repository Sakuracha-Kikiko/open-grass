package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.model.LBGBakedModel;
import com.sakuracha.opengrass.client.model.LBGModelBaker;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a grass better grass state, which connects the side texture of a block to its neighbors.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGGrassState extends LBGState {
    private final Map<String, LBGMetadata> metadatas = new LinkedHashMap<>();
    private final @Nullable LBGMetadata metadata;

    public LBGGrassState(ResourceLocation id, Block block, JsonObject json, ResourceManager resourceManager) {
        super(id);

        if (json.has("variants")) {
            var variants = json.getAsJsonObject("variants");

            for (var entry : variants.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                var variant = entry.getValue().getAsJsonObject();
                if (!variant.has("data")) {
                    continue;
                }

                var variantMetadata = this.loadMetadata(resourceManager, ResourceLocation.parse(variant.get("data").getAsString()));
                if (variantMetadata != null) {
                    this.metadatas.put(entry.getKey(), variantMetadata);
                }
            }

            var normalMetadata = this.metadatas.get("snowy=false");
            var snowyMetadata = this.metadatas.get("snowy=true");
            if (normalMetadata != null && snowyMetadata != null) {
                snowyMetadata.setSnowyModelVariantProvider(bakedModel -> normalMetadata.setSnowyModelVariant(bakedModel));
            }

            this.metadata = null;
        } else if (json.has("data")) {
            this.metadata = this.loadMetadata(resourceManager, ResourceLocation.parse(json.get("data").getAsString()));
        } else {
            OpenGrass.LOGGER.warn("Invalid state definition for {}, missing data or variants entry.", id);
            this.metadata = null;
        }
    }

    private @Nullable LBGMetadata loadMetadata(ResourceManager resourceManager, ResourceLocation metadataId) {
        var resourceId = ResourceLocation.fromNamespaceAndPath(metadataId.getNamespace(), metadataId.getPath() + ".json");

        try (BufferedReader reader = resourceManager.getResourceOrThrow(resourceId).openAsReader()) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            return new LBGMetadata(resourceManager, metadataId, json);
        } catch (IOException | RuntimeException e) {
            OpenGrass.LOGGER.warn("Could not load metadata {}.", metadataId, e);
            return null;
        }
    }

    /**
     * Checks if this state is the one which provides the snowy model variant, which means it has to be baked first.
     *
     * @param variant the model variant
     * @return {@code true} if this state provides the snowy model variant
     */
    public boolean isSnowyProvider(String variant) {
        return this.metadatas.containsKey("snowy=true") && matchVariant(variant, "snowy=true");
    }

    public @Nullable LBGMetadata getMetadata(String variant) {
        if (this.metadata != null) {
            return this.metadata;
        }

        for (var entry : this.metadatas.entrySet()) {
            if (matchVariant(variant, entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public @Nullable BakedModel wrap(BlockState state, String variant, BakedModel base, LBGModelBaker baker,
            java.util.function.Function<Material, TextureAtlasSprite> sprites) {
        var metadata = this.getMetadata(variant);
        if (metadata == null) {
            return null;
        }

        metadata.bakeTextures(sprites);
        var model = new LBGBakedModel(base, metadata);
        metadata.propagate(model);
        return model;
    }
}
