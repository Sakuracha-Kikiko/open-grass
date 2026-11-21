package com.sakuracha.opengrass;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import com.sakuracha.opengrass.client.event.LBGClientEvents;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Open Grass: an open-source port of LambdaBetterGrass to Minecraft 1.21.1 / NeoForge.
 *
 * @author Sakuracha Kikiko
 */
@Mod(OpenGrass.MOD_ID)
public final class OpenGrass {
    public static final String MOD_ID = "open_grass";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation MASK_CONNECT = id("bettergrass/mask/standard_block_side_connect.png");
    public static final ResourceLocation MASK_BLEND_UP = id("bettergrass/mask/grass_block_side_blend_up.png");
    public static final ResourceLocation MASK_ARCH = id("bettergrass/mask/grass_block_side_arch_blend.png");

    public OpenGrass(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modEventBus.addListener(Config::onLoading);
        modEventBus.addListener(Config::onReloading);

        if (FMLEnvironment.dist.isClient()) {
            LBGClientEvents.register(modEventBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
