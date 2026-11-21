package com.sakuracha.opengrass.client.event;

import com.mojang.serialization.MapCodec;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.metadata.LBGGrassState;
import com.sakuracha.opengrass.client.metadata.LBGState;
import com.sakuracha.opengrass.client.model.LBGBakedModel;
import com.sakuracha.opengrass.client.model.LBGModelBaker;
import com.sakuracha.opengrass.client.model.LBGRenderTypeBakedModel;
import com.sakuracha.opengrass.client.resource.LBGSpriteSource;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
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
        var baker = new LBGModelBaker(resourceManager, sprites);
        var stateCache = new HashMap<Block, Map<String, BlockState>>();

        // The states which provide the snowy model variant have to be wrapped first so the normal variant can
        // reference their baked model.
        for (boolean snowyPass : new boolean[]{true, false}) {
            for (var entry : models.entrySet()) {
                wrapModel(entry, snowyPass, sprites, baker, stateCache);
            }
        }

        applyRenderTypes(models);
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

    private static void applyRenderTypes(Map<ModelResourceLocation, BakedModel> models) {
        for (var layerType : LBGData.layerTypes()) {
            var renderType = layerType.forcedRenderLayer();
            if (renderType == null) {
                continue;
            }

            var blockId = BuiltInRegistries.BLOCK.getKey(layerType.block());
            ResourceLocation currentId = blockId;

            for (var state : layerType.block().getStateDefinition().getPossibleStates()) {
                var location = BlockModelShaper.stateToModelLocation(currentId, state);
                var model = models.get(location);
                if (model != null && !(model instanceof LBGRenderTypeBakedModel)) {
                    models.put(location, new LBGRenderTypeBakedModel(model, renderType));
                }
            }
        }
    }
}
