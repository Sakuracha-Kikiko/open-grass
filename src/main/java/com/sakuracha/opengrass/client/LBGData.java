package com.sakuracha.opengrass.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.metadata.LBGLayerType;
import com.sakuracha.opengrass.client.metadata.LBGState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Holds all the data loaded from the resource packs.
 *
 * <p>This is refreshed during the sprite atlas stitiching phase (which happens before models are baked),
 * so the model baking phase can rely on the data loaded here.</p>
 *
 * @author Sakuracha Kikiko
 */
public final class LBGData {
    public static final ModelProperty<BlockContext> BLOCK_CONTEXT = new ModelProperty<>();

    private static final Map<ResourceLocation, LBGState> STATES = new HashMap<>();
    private static final List<LBGLayerType> LAYER_TYPES = new ArrayList<>();
    private static final Map<ResourceLocation, NativeImage> IMAGES = new LinkedHashMap<>();

    private static ResourceManager resourceManager;
    private static int imageCounter;

    private LBGData() {
    }

    public record BlockContext(BlockAndTintGetter level, BlockPos pos) {
    }

    public static ResourceManager resourceManager() {
        return resourceManager;
    }

    public static Collection<LBGState> states() {
        return STATES.values();
    }

    public static LBGState state(Block block) {
        return STATES.get(BuiltInRegistries.BLOCK.getKey(block));
    }

    /**
     * Gets the state registered for the given identifier.
     *
     * @param id the identifier of the state
     * @return the state, or {@code null} if there is none
     */
    public static LBGState state(ResourceLocation id) {
        return STATES.get(id);
    }

    public static LBGState state(BlockState state) {
        return state(state.getBlock());
    }

    public static List<LBGLayerType> layerTypes() {
        return LAYER_TYPES;
    }

    public static Map<ResourceLocation, NativeImage> images() {
        return IMAGES;
    }

    /**
     * Registers a dynamically generated image as a sprite of the block atlas.
     *
     * @param name the name of the image
     * @param image the image
     * @return the identifier of the generated sprite
     */
    public static ResourceLocation addImage(String name, NativeImage image) {
        var id = OpenGrass.id("block/bettergrass/" + name + "_" + imageCounter++);
        IMAGES.put(id, image);
        return id;
    }

    public static void removeImage(ResourceLocation id) {
        IMAGES.remove(id);
    }

    public static String variantOf(BlockState state) {
        return BlockModelShaper.statePropertiesToString(state.getValues());
    }

    /**
     * Reloads the data from the resource manager.
     *
     * @param resourceManager the resource manager
     */
    public static void reload(ResourceManager resourceManager) {
        LBGData.resourceManager = resourceManager;
        STATES.clear();
        LAYER_TYPES.clear();
        IMAGES.clear();
        imageCounter = 0;

        var layerTypes = resourceManager.listResources("bettergrass/layer_types", path -> path.getPath().endsWith(".json"));
        for (var entry : layerTypes.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                LBGLayerType.load(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                OpenGrass.LOGGER.warn("Failed to load layer type {}.", entry.getKey(), e);
            }
        }

        var states = resourceManager.listResources(LBGState.PATH_PREFIX, path -> path.getPath().endsWith(".json"));
        for (var entry : states.entrySet()) {
            var path = entry.getKey();
            var file = path.getPath().substring(LBGState.PATH_PREFIX.length() + 1);
            if (!file.endsWith(".json")) {
                continue;
            }

            var blockId = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), file.substring(0, file.length() - 5));
            var block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
            if (block == null) {
                OpenGrass.LOGGER.debug("Ignoring state {} as its block does not exist.", blockId);
                continue;
            }

            try (var reader = entry.getValue().openAsReader()) {
                var state = LBGState.create(blockId, block, JsonParser.parseReader(reader).getAsJsonObject(), resourceManager);
                if (state != null) {
                    STATES.put(blockId, state);
                }
            } catch (Exception e) {
                OpenGrass.LOGGER.warn("Failed to load state {} for block {}.", path, blockId, e);
            }
        }

        OpenGrass.LOGGER.info("Loaded {} better grass states and {} layer types.", STATES.size(), LAYER_TYPES.size());
    }

    public static Optional<Resource> getResource(ResourceLocation id) {
        return resourceManager == null ? Optional.empty() : resourceManager.getResource(id);
    }
}
