package com.sakuracha.opengrass.client;

import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.gui.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * The client entrypoint of Open Grass.
 *
 * @author Sakuracha Kikiko
 */
@Mod(value = OpenGrass.MOD_ID, dist = Dist.CLIENT)
public final class OpenGrassClient {
    public OpenGrassClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (modContainer, parent) -> new SettingsScreen(parent));
    }
}
