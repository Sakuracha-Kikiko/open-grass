package com.sakuracha.opengrass;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client configuration of Open Grass.
 *
 * @author Sakuracha Kikiko
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<LBGMode> MODE;
    public static final ModConfigSpec.BooleanValue BETTER_SNOW;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("better_grass");
        MODE = BUILDER
                .comment("The better grass mode.", "Available values: OFF, FASTEST, FAST, FANCY.")
                .defineEnum("mode", LBGMode.FANCY);
        BETTER_SNOW = BUILDER
                .comment("Whether the better snow feature is enabled.")
                .define("better_snow", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private static volatile LBGMode mode = LBGMode.FANCY;
    private static volatile boolean betterSnow = true;

    private Config() {
    }

    public static synchronized LBGMode mode() {
        return mode;
    }

    public static synchronized boolean betterSnow() {
        return betterSnow;
    }

    public static void setMode(LBGMode value) {
        mode = value;
        try {
            MODE.set(value);
            SPEC.save();
        } catch (IllegalStateException ignored) {
        }
    }

    public static void setBetterSnow(boolean value) {
        betterSnow = value;
        try {
            BETTER_SNOW.set(value);
            SPEC.save();
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Resets the configuration to its default values.
     */
    public static void reset() {
        setMode(LBGMode.FANCY);
        setBetterSnow(true);
    }

    public static void onLoading(ModConfigEvent.Loading event) {
        refresh(event);
    }

    public static void onReloading(ModConfigEvent.Reloading event) {
        refresh(event);
    }

    private static void refresh(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        try {
            mode = MODE.get();
        } catch (IllegalStateException e) {
            mode = LBGMode.FANCY;
        }

        try {
            betterSnow = BETTER_SNOW.get();
        } catch (IllegalStateException e) {
            betterSnow = true;
        }
    }
}
