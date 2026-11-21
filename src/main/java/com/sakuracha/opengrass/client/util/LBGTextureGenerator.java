package com.sakuracha.opengrass.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.sakuracha.opengrass.OpenGrass;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities to generate the better grass textures.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGTextureGenerator {
    private static final ResourceLocation FALLBACK_TEXTURE = OpenGrass.id("textures/block/transparent.png");

    private LBGTextureGenerator() {
    }

    public static NativeImage getFallbackNativeImage(ResourceManager resourceManager) {
        try (InputStream stream = resourceManager.getResourceOrThrow(FALLBACK_TEXTURE).open()) {
            return NativeImage.read(stream);
        } catch (IOException e) {
            OpenGrass.LOGGER.warn("Could not load fallback texture, using an empty image instead.", e);
            return new NativeImage(16, 16, false);
        }
    }

    public static NativeImage getNativeImage(ResourceManager resourceManager, @Nullable ResourceLocation texture) {
        if (texture == null) {
            return getFallbackNativeImage(resourceManager);
        }

        try (InputStream stream = resourceManager.getResourceOrThrow(texture).open()) {
            return NativeImage.read(stream);
        } catch (IOException | RuntimeException e) {
            OpenGrass.LOGGER.warn("Could not load texture {}. Loading fallback texture instead.", texture, e);
            return getFallbackNativeImage(resourceManager);
        }
    }

    public static NativeImage mirrorImage(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        var result = new NativeImage(width, height, false);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                result.setPixelRGBA(width - 1 - x, y, source.getPixelRGBA(x, y));
            }
        }

        return result;
    }

    /**
     * Applies the mask to the source texture, using the top texture where the mask is opaque.
     *
     * @param source the side texture
     * @param top the top texture
     * @param mask the mask
     * @return the generated texture
     */
    public static NativeImage applyMask(NativeImage source, NativeImage top, NativeImage mask) {
        int width = Math.max(source.getWidth(), Math.max(top.getWidth(), mask.getWidth()));
        int height = Math.max(source.getHeight(), Math.max(top.getHeight(), mask.getHeight()));
        var output = new NativeImage(width, height, false);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int maskPixel = mask.getPixelRGBA(
                        getTrueCoordinate(width, mask.getWidth(), x),
                        getTrueCoordinate(height, mask.getHeight(), y)
                );
                int pixel;

                if (((maskPixel >> 24) & 0xFF) == 255) {
                    pixel = top.getPixelRGBA(
                            getTrueCoordinate(width, top.getWidth(), x),
                            getTrueCoordinate(height, top.getHeight(), y)
                    );
                } else {
                    pixel = source.getPixelRGBA(
                            getTrueCoordinate(width, source.getWidth(), x),
                            getTrueCoordinate(height, source.getHeight(), y)
                    );
                }

                output.setPixelRGBA(x, y, pixel);
            }
        }

        return output;
    }

    public static int getTrueCoordinate(int resolution, int targetResolution, int coordinate) {
        if (resolution == targetResolution) {
            return coordinate;
        }

        return (int) ((coordinate / (double) resolution) * targetResolution);
    }
}
