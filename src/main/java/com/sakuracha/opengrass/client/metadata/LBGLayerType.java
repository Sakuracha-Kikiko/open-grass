package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.LBGData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a layer type, which is a block that can be layered over another block (snow, moss, sculk, ...).
 *
 * <p>A layer type describes which block state is rendered as the layer, and which blocks count as that layer
 * when looking for neighbouring layers. Both are configurable through the layer type file:</p>
 * <ul>
 *     <li>{@code block}: the block the layer type is about, also used as the default match and for the render layer;</li>
 *     <li>{@code display}: the block state rendered as the layer, defaults to the default state of {@code block};</li>
 *     <li>{@code match}: the blocks which count as this layer, either block identifiers or {@code #tags},
 *     defaults to {@code block} only;</li>
 *     <li>{@code model}: a legacy custom model rendered instead of {@code display}.</li>
 * </ul>
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
    private final BlockState displayState;
    private final @Nullable ResourceLocation legacyModelId;
    private final Predicate<BlockState> matcher;
    private final String name;
    private final Set<RenderType> acceptedRenderLayers;
    private final @Nullable RenderType defaultRenderLayer;
    private final @Nullable RenderType forcedRenderLayer;

    private LBGLayerType(ResourceLocation id, Block block, BlockState displayState, @Nullable ResourceLocation legacyModelId,
            Predicate<BlockState> matcher, Set<RenderType> acceptedRenderLayers, @Nullable RenderType defaultRenderLayer,
            @Nullable RenderType forcedRenderLayer) {
        this.id = id;
        this.block = block;
        this.displayState = displayState;
        this.legacyModelId = legacyModelId;
        this.matcher = matcher;
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

    /**
     * Returns the block state rendered as this layer.
     *
     * @return the display state
     */
    public BlockState displayState() {
        return this.displayState;
    }

    /**
     * Returns the legacy custom model of this layer type, if any.
     *
     * @return the legacy model identifier, or {@code null} if the layer renders a block state instead
     */
    public @Nullable ResourceLocation legacyModelId() {
        return this.legacyModelId;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Checks whether the given block state counts as this layer when looking for neighbouring layers.
     *
     * @param state the block state
     * @return {@code true} if the block state is part of this layer type
     */
    public boolean matches(BlockState state) {
        return this.matcher.test(state);
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

        if (!json.has("block")) {
            OpenGrass.LOGGER.warn("Invalid layer type {}, missing block entry.", id);
            return;
        }

        var blockId = ResourceLocation.parse(json.get("block").getAsString());
        var block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null || block == Blocks.AIR) {
            OpenGrass.LOGGER.debug("Ignoring layer type {} as its block {} does not exist.", id, blockId);
            return;
        }

        var displayState = parseDisplayState(id, block, json);
        var legacyModelId = json.has("model") ? ResourceLocation.parse(json.get("model").getAsString()) : null;
        var matcher = parseMatcher(id, block, json);

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

        LBGData.layerTypes().add(new LBGLayerType(id, block, displayState, legacyModelId, matcher, accepted, defaultRenderLayer, forcedRenderLayer));
    }

    private static BlockState parseDisplayState(ResourceLocation id, Block block, JsonObject json) {
        if (!json.has("display")) {
            return block.defaultBlockState();
        }

        var value = json.get("display").getAsString();

        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), value, false).blockState();
        } catch (CommandSyntaxException e) {
            OpenGrass.LOGGER.warn("Cannot parse the display state '{}' of layer type {}, falling back to the default state of {}.",
                    value, id, BuiltInRegistries.BLOCK.getKey(block));
            return block.defaultBlockState();
        }
    }

    private static Predicate<BlockState> parseMatcher(ResourceLocation id, Block block, JsonObject json) {
        if (!json.has("match")) {
            return state -> state.getBlock() == block;
        }

        var values = new ArrayList<String>();
        var element = json.get("match");
        if (element.isJsonArray()) {
            for (var entry : element.getAsJsonArray()) {
                values.add(entry.getAsString());
            }
        } else {
            values.add(element.getAsString());
        }

        var matchers = new ArrayList<Predicate<BlockState>>();
        for (var value : values) {
            if (value.startsWith("#")) {
                var tagId = ResourceLocation.tryParse(value.substring(1));
                if (tagId == null) {
                    OpenGrass.LOGGER.warn("Invalid tag '{}' in the match list of layer type {}.", value, id);
                    continue;
                }

                var tag = TagKey.create(Registries.BLOCK, tagId);
                matchers.add(state -> state.is(tag));
            } else {
                var other = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(value)).orElse(null);
                if (other == null || other == Blocks.AIR) {
                    OpenGrass.LOGGER.warn("Unknown block '{}' in the match list of layer type {}.", value, id);
                    continue;
                }

                matchers.add(state -> state.getBlock() == other);
            }
        }

        if (matchers.isEmpty()) {
            return state -> state.getBlock() == block;
        }

        var predicates = List.copyOf(matchers);
        return state -> {
            for (var predicate : predicates) {
                if (predicate.test(state)) {
                    return true;
                }
            }

            return false;
        };
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
