package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.model.LBGLayerBakedModel;
import com.sakuracha.opengrass.client.model.LBGModelBaker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a layer better grass state, which renders an extra block over a block (snow, moss, sculk, ...).
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGLayerState extends LBGState {
    private static final JsonObject DEFAULT_METADATA_LAYER_JSON = new JsonObject();

    static {
        DEFAULT_METADATA_LAYER_JSON.addProperty("layer", true);
    }

    private final Block block;
    private final Map<String, List<LBGLayerMetadata>> metadatas = new LinkedHashMap<>();

    public LBGLayerState(ResourceLocation id, Block block, JsonObject json, ResourceManager resourceManager) {
        super(id);
        this.block = block;

        if (json.has("variants")) {
            for (var entry : json.getAsJsonObject("variants").entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                var variant = entry.getValue().getAsJsonObject();
                if (variant.has("data")) {
                    this.loadVariant(entry.getKey(), variant, resourceManager);
                }
            }
        } else if (json.has("data")) {
            this.loadVariant("*", json, resourceManager);
        } else {
            OpenGrass.LOGGER.warn("Invalid state definition for {}, missing data or variants entry.", id);
        }
    }

    private void loadVariant(String variant, JsonObject json, ResourceManager resourceManager) {
        var metadataId = ResourceLocation.parse(json.get("data").getAsString());

        for (var layerType : LBGData.layerTypes()) {
            this.putOrReplaceMetadata(variant, layerType, DEFAULT_METADATA_LAYER_JSON);
        }

        var resourceId = ResourceLocation.fromNamespaceAndPath(metadataId.getNamespace(), metadataId.getPath() + ".json");
        for (var resource : resourceManager.getResourceStack(resourceId)) {
            try (var reader = resource.openAsReader()) {
                var obj = JsonParser.parseReader(reader).getAsJsonObject();
                for (var entry : obj.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }

                    var layerType = LBGLayerType.fromName(entry.getKey());
                    if (layerType != null) {
                        this.putOrReplaceMetadata(variant, layerType, entry.getValue().getAsJsonObject());
                    }
                }
            } catch (Exception e) {
                OpenGrass.LOGGER.warn("Cannot load metadata file {} of state {}.", resourceId, this.id, e);
            }
        }
    }

    private void putOrReplaceMetadata(String variant, LBGLayerType layerType, JsonObject json) {
        var list = this.metadatas.computeIfAbsent(variant, k -> new ArrayList<>());
        list.removeIf(metadata -> metadata.layerType() == layerType);
        list.add(new LBGLayerMetadata(layerType, this.block.getStateDefinition(), json));
    }

    /**
     * Iterates over the metadata of the first variant matching the given model variant.
     *
     * @param variant the model variant
     * @param consumer the consumer
     */
    public void forEach(String variant, Consumer<LBGLayerMetadata> consumer) {
        for (var entry : this.metadatas.entrySet()) {
            if (entry.getKey().equals("*") || matchVariant(variant, entry.getKey())) {
                entry.getValue().forEach(consumer);
                return;
            }
        }
    }

    @Override
    public @Nullable BakedModel wrap(BlockState state, String variant, BakedModel base, LBGModelBaker baker,
            java.util.function.Function<Material, TextureAtlasSprite> sprites) {
        for (var entry : this.metadatas.entrySet()) {
            if (!entry.getKey().equals("*") && !matchVariant(variant, entry.getKey())) {
                continue;
            }

            var compiled = new ArrayList<LBGCompiledLayerMetadata>();
            for (var metadata : entry.getValue()) {
                var layerMetadata = metadata.bake(baker, sprites, variant);
                if (layerMetadata != null) {
                    compiled.add(layerMetadata);
                }
            }

            return compiled.isEmpty() ? null : new LBGLayerBakedModel(base, compiled);
        }

        return null;
    }
}
