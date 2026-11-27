package com.sakuracha.opengrass.client.event;

import com.mojang.serialization.MapCodec;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.metadata.LBGGrassState;
import com.sakuracha.opengrass.client.metadata.LBGLayerState;
import com.sakuracha.opengrass.client.model.LBGModelBaker;
import com.sakuracha.opengrass.client.model.LBGRenderTypeBakedModel;
import com.sakuracha.opengrass.client.resource.LBGSpriteSource;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Handles the client events of the mod.
 *
 * @author Sakuracha Kikiko
 */
public final class LBGClientEvents {
    private static @Nullable SpriteSourceType spriteSourceType;

    private LBGClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(LBGClientEvents::onRegisterSpriteSourceTypes);
        modEventBus.addListener(LBGClientEvents::onModifyBakingResult);
    }

    public static SpriteSourceType spriteSourceType() {
        if (spriteSourceType == null) {
            throw new IllegalStateException("The sprite source type has not been registered yet.");
        }

        return spriteSourceType;
    }

    public static void onRegisterSpriteSourceTypes(RegisterSpriteSourceTypesEvent event) {
        var type = new SpriteSourceType(MapCodec.unit(LBGSpriteSource.INSTANCE));
        spriteSourceType = type;
        event.register(OpenGrass.id("generated"), type);
    }

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var resourceManager = LBGData.resourceManager();
        if (resourceManager == null) {
            return;
        }

        var models = event.getModels();
        var sprites = event.getTextureGetter();
        // Snapshot the baked models before wrapping any of them, as the models of the layer display states
        // have to be the vanilla ones and not the wrappers added below.
        var bakedModels = Map.copyOf(models);
        var baker = new LBGModelBaker(resourceManager, sprites, bakedModels);
        var stateCache = new HashMap<Block, Map<String, BlockState>>();

        // The states which provide the snowy model variant have to be wrapped first so the normal variant can
        // reference their baked model.
        for (boolean snowyPass : new boolean[]{true, false}) {
            for (var entry : models.entrySet()) {
                wrapModel(entry, snowyPass, sprites, baker, stateCache);
            }
        }

        applyRenderTypes(models, stateCache);
    }

    private static void wrapModel(Map.Entry<ModelResourceLocation, BakedModel> entry, boolean snowyPass,
            java.util.function.Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> sprites,
            LBGModelBaker baker, Map<Block, Map<String, BlockState>> stateCache) {
        var key = entry.getKey();
        if (key.getVariant().equals(ModelResourceLocation.INVENTORY_VARIANT)) {
            return;
        }

        var block = BuiltInRegistries.BLOCK.getOptional(key.id()).orElse(null);
        if (block == null) {
            return;
        }

        var state = LBGData.state(block);
        if (state == null) {
            return;
        }

        boolean snowyProvider = state instanceof LBGGrassState grass && grass.isSnowyProvider(key.getVariant());
        if (snowyProvider != snowyPass) {
            return;
        }

        var blockState = stateFor(block, key.getVariant(), stateCache);
        if (blockState == null) {
            return;
        }

        var wrapped = state.wrap(blockState, key.getVariant(), entry.getValue(), baker, sprites);
        if (wrapped != null) {
            entry.setValue(wrapped);
        }
    }

    private static @Nullable BlockState stateFor(Block block, String variant, Map<Block, Map<String, BlockState>> cache) {
        var states = cache.computeIfAbsent(block, key -> {
            var result = new HashMap<String, BlockState>();
            for (var state : key.getStateDefinition().getPossibleStates()) {
                result.put(LBGData.variantOf(state), state);
            }
            return result;
        });

        return states.get(variant);
    }

    /**
     * Forces the render type of the blocks covered by a layer when it cannot render the transparency of the
     * layer texture, as the layer is rendered in the render type of the block it covers.
     */
    private static void applyRenderTypes(Map<ModelResourceLocation, BakedModel> models, Map<Block, Map<String, BlockState>> stateCache) {
        var random = RandomSource.create(0L);
        var forcedRenderTypes = new HashMap<Block, @Nullable RenderType>();

        for (var entry : models.entrySet()) {
            var key = entry.getKey();
            if (key.getVariant().equals(ModelResourceLocation.INVENTORY_VARIANT)) {
                continue;
            }

            var block = BuiltInRegistries.BLOCK.getOptional(key.id()).orElse(null);
            if (block == null || !(LBGData.state(block) instanceof LBGLayerState)) {
                continue;
            }

            if (!forcedRenderTypes.containsKey(block)) {
                var blockState = stateFor(block, key.getVariant(), stateCache);
                RenderType forced = null;
                if (blockState != null) {
                    var renderTypes = entry.getValue().getRenderTypes(blockState, random, ModelData.EMPTY);
                    for (var layerType : LBGData.layerTypes()) {
                        forced = layerType.renderTypeFor(renderTypes);
                        if (forced != null) {
                            break;
                        }
                    }
                }

                forcedRenderTypes.put(block, forced);
            }

            var renderType = forcedRenderTypes.get(block);
            if (renderType == null || entry.getValue() instanceof LBGRenderTypeBakedModel) {
                continue;
            }

            entry.setValue(new LBGRenderTypeBakedModel(entry.getValue(), renderType));
        }
    }
}
