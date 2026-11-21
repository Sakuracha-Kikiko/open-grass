package com.sakuracha.opengrass;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Represents the better grass mode.
 *
 * @author LambdaURORA, Sakuracha Kikiko
 */
public enum LBGMode {
    OFF(ChatFormatting.RED),
    FASTEST(ChatFormatting.GOLD),
    FAST(ChatFormatting.YELLOW),
    FANCY(ChatFormatting.GREEN);

    private final ChatFormatting color;

    LBGMode(ChatFormatting color) {
        this.color = color;
    }

    public boolean isEnabled() {
        return this != OFF;
    }

    public LBGMode next() {
        var values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public Component getText() {
        return Component.translatable("open_grass.mode." + this.getName()).withStyle(this.color);
    }

    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static LBGMode byId(String id) {
        for (var mode : values()) {
            if (mode.getName().equalsIgnoreCase(id)) {
                return mode;
            }
        }

        return FANCY;
    }
}
