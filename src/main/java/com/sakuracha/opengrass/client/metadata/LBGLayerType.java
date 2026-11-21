package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.LBGData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a layer type, which is a block that can be layered over another block (snow, moss, sculk, ...).
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGLayerType {
    private static final Map<String, RenderType> NAMED_RENDER_LAYERS = new HashMap<>();

    static {
        NAMED_RENDER_LAYERS.put("solid", RenderType.solid());
        NAMED_RENDER_LAYERS.put("cutout", RenderType.cutout());
        NAMED_RENDER_LAYERS.put("cutout_mipped", RenderType.cutoutMipped());
        NAMED_RENDER_LAYERS.put("translucent", RenderType.translucent());
        NAMED_RENDER_LAYERS.put("tripwire", RenderType.tripwire());
    }

    private final ResourceLocation id;
    private final Block block;
    private final ResourceLocation modelId;
    private final String name;
    private final Set<RenderType> acceptedRenderLayers;
    private final @Nullable RenderType defaultRenderLayer;
    private final @Nullable RenderType forcedRenderLayer;

    private LBGLayerType(ResourceLocation id, Block block, ResourceLocation modelId, Set<RenderType> acceptedRenderLayers,
            @Nullable RenderType defaultRenderLayer, @Nullable RenderType forcedRenderLayer) {
        this.id = id;
        this.block = block;
        this.modelId = modelId;
        this.name = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        this.acceptedRenderLayers = acceptedRenderLayers;
        this.defaultRenderLayer = defaultRenderLayer;
        this.forcedRenderLayer = forcedRenderLayer;
    }

    public ResourceLocation id() {
        return this.id;
    }

    public Block block() {
        return this.block;
    }

    public ResourceLocation modelId() {
        return this.modelId;
    }

    public String getName() {
        return this.name;
    }

    public boolean isFor(Block block) {
        return this.block == block;
    }

    public @Nullable RenderType defaultRenderLayer() {
        return this.defaultRenderLayer;
    }

    public @Nullable RenderType forcedRenderLayer() {
        return this.forcedRenderLayer;
    }

    public static void load(ResourceLocation resourceId, JsonObject json) {
        var path = resourceId.getPath();
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }

        var id = ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path);

        if (!json.has("block") || !json.has("model")) {
            OpenGrass.LOGGER.warn("Invalid layer type {}.", id);
            return;
        }

        var blockId = ResourceLocation.parse(json.get("block").getAsString());
        var block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            OpenGrass.LOGGER.debug("Ignoring layer type {} as its block {} does not exist.", id, blockId);
            return;
        }

        var modelId = ResourceLocation.parse(json.get("model").getAsString());
        var accepted = new HashSet<RenderType>();
        RenderType defaultRenderLayer = null;

        if (json.has("render_layer")) {
            var renderLayer = json.getAsJsonObject("render_layer");

            if (renderLayer.has("accepted")) {
                for (var element : renderLayer.getAsJsonArray("accepted")) {
                    var type = NAMED_RENDER_LAYERS.get(element.getAsString());
                    if (type != null) {
                        accepted.add(type);
                    } else {
                        OpenGrass.LOGGER.warn("Failed to find accepted render layer {} for layer type {}.", element.getAsString(), id);
                    }
                }
            }

            if (renderLayer.has("default")) {
                defaultRenderLayer = NAMED_RENDER_LAYERS.get(renderLayer.get("default").getAsString());
                if (defaultRenderLayer == null) {
                    OpenGrass.LOGGER.warn("Failed to find default render layer {} for layer type {}.", renderLayer.get("default").getAsString(), id);
                }
            }
        }

        RenderType forcedRenderLayer = null;
        if (defaultRenderLayer != null) {
            var currentRenderLayer = ItemBlockRenderTypes.getChunkRenderType(block.defaultBlockState());
            if (currentRenderLayer != defaultRenderLayer && !accepted.contains(currentRenderLayer)) {
                forcedRenderLayer = defaultRenderLayer;
            }
        }

        LBGData.layerTypes().add(new LBGLayerType(id, block, modelId, accepted, defaultRenderLayer, forcedRenderLayer));
    }

    public static @Nullable LBGLayerType fromName(String name) {
        for (var type : LBGData.layerTypes()) {
            if (type.name.equals(name)) {
                return type;
            }
        }

        return null;
    }
}
