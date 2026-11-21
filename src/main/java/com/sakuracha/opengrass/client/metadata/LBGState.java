package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.model.LBGModelBaker;
import java.util.ArrayList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a better grass state, which describes how a block should be rendered.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public abstract class LBGState {
    public static final String PATH_PREFIX = "bettergrass/states";

    protected final ResourceLocation id;

    protected LBGState(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation id() {
        return this.id;
    }

    /**
     * Checks if the given model variant matches the given data variant.
     *
     * @param modelVariant the model variant, as computed from a block state
     * @param dataVariant the data variant, as written in the data file
     * @return {@code true} if the data variant matches the model variant
     */
    protected static boolean matchVariant(String modelVariant, String dataVariant) {
        if (dataVariant.equals("*")) {
            return true;
        }

        var modelProperties = splitVariant(modelVariant);
        for (String dataProperty : splitVariant(dataVariant)) {
            if (!modelProperties.contains(dataProperty)) {
                return false;
            }
        }

        return true;
    }

    private static ArrayList<String> splitVariant(String variant) {
        var list = new ArrayList<String>();
        for (String part : variant.split(",")) {
            if (!part.isEmpty()) {
                list.add(part);
            }
        }
        return list;
    }

    /**
     * Wraps the baked model of the block state with the better grass model.
     *
     * @param state the block state
     * @param variant the model variant of the block state
     * @param base the base baked model
     * @param baker the model baker
     * @param sprites the texture getter
     * @return the wrapped model, or {@code null} if this state does not apply
     */
    public abstract @Nullable BakedModel wrap(BlockState state, String variant, BakedModel base, LBGModelBaker baker,
            java.util.function.Function<Material, TextureAtlasSprite> sprites);

    public static @Nullable LBGState create(ResourceLocation id, Block block, JsonObject json, ResourceManager resourceManager) {
        String type = json.has("type") ? json.get("type").getAsString() : "grass";

        return switch (type) {
            case "grass" -> new LBGGrassState(id, block, json, resourceManager);
            case "layer" -> new LBGLayerState(id, block, json, resourceManager);
            default -> {
                OpenGrass.LOGGER.warn("Could not find type {} for metadata state {}.", type, id);
                yield null;
            }
        };
    }
}
