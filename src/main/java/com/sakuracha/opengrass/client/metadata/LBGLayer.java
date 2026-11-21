package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.sakuracha.opengrass.OpenGrass;
import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.util.LBGTextureGenerator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a layer of a better grass metadata, which generates the side textures.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGLayer {
    private static final String[] TEXTURE_KINDS = {"connect", "blend_up", "blend_up_m", "arch"};

    private final LBGMetadata parentMetadata;
    private final int index;
    private final int colorIndex;
    private final @Nullable ResourceLocation connectMask;
    private final @Nullable ResourceLocation blendUpMask;
    private final @Nullable ResourceLocation archMask;
    private final ResourceLocation topTexture;
    private final ResourceLocation sideTexture;
    private final Map<String, ResourceLocation> overrides = new LinkedHashMap<>();
    private final Map<String, ResourceLocation> generated = new LinkedHashMap<>();
    private final Map<String, NativeImage> images = new HashMap<>();
    private final Map<String, TextureAtlasSprite> bakedSprites = new HashMap<>();

    public LBGLayer(LBGMetadata parentMetadata, JsonObject json) {
        this.parentMetadata = parentMetadata;
        this.index = parentMetadata.nextLayerIndex();
        this.colorIndex = json.has("color_index") ? json.get("color_index").getAsInt() : -1;

        var textures = json.getAsJsonObject("textures");
        this.topTexture = textures != null && textures.has("top")
                ? ResourceLocation.parse(textures.get("top").getAsString())
                : ResourceLocation.withDefaultNamespace("missingno");
        this.sideTexture = textures != null && textures.has("side")
                ? ResourceLocation.parse(textures.get("side").getAsString())
                : ResourceLocation.withDefaultNamespace("missingno");

        if (textures != null && textures.has("overrides")) {
            var overrides = textures.getAsJsonObject("overrides");
            for (var entry : overrides.entrySet()) {
                this.overrides.put(entry.getKey(), ResourceLocation.parse(entry.getValue().getAsString()));
            }
        }

        var masks = json.getAsJsonObject("masks");
        this.connectMask = getMask(masks, "connect", OpenGrass.MASK_CONNECT);
        this.blendUpMask = getMask(masks, "blend_up", OpenGrass.MASK_BLEND_UP);
        this.archMask = getMask(masks, "arch", OpenGrass.MASK_ARCH);
    }

    private static @Nullable ResourceLocation getMask(@Nullable JsonObject masks, String key, ResourceLocation fallback) {
        if (masks != null && masks.has(key)) {
            var value = masks.get(key).getAsString();
            return ResourceLocation.parse(value.endsWith(".png") ? value : value + ".png");
        }

        return fallback;
    }

    public int colorIndex() {
        return this.colorIndex;
    }

    public int index() {
        return this.index;
    }

    public @Nullable TextureAtlasSprite getBakedTexture(String kind) {
        return this.bakedSprites.get(kind);
    }

    /**
     * Generates the textures of this layer.
     *
     * @param resourceManager the resource manager
     */
    public void buildTextures(ResourceManager resourceManager) {
        var side = LBGTextureGenerator.getNativeImage(resourceManager, getTexturePath(this.sideTexture));
        var top = LBGTextureGenerator.getNativeImage(resourceManager, getTexturePath(this.topTexture));
        var name = textureName(this.parentMetadata.id);

        var connectMaskImage = LBGTextureGenerator.getNativeImage(resourceManager, this.connectMask);
        this.register("connect", LBGTextureGenerator.applyMask(side, top, connectMaskImage), name);
        connectMaskImage.close();

        var blendUpMaskImage = LBGTextureGenerator.getNativeImage(resourceManager, this.blendUpMask);
        this.register("blend_up", LBGTextureGenerator.applyMask(side, top, blendUpMaskImage), name);
        // Mirror the mask, not the composed texture: only the grass shape may flip, the dirt below it must stay as-is.
        var mirroredBlendUpMaskImage = LBGTextureGenerator.mirrorImage(blendUpMaskImage);
        blendUpMaskImage.close();
        this.register("blend_up_m", LBGTextureGenerator.applyMask(side, top, mirroredBlendUpMaskImage), name);
        mirroredBlendUpMaskImage.close();

        var archMaskImage = LBGTextureGenerator.getNativeImage(resourceManager, this.archMask);
        this.register("arch", LBGTextureGenerator.applyMask(side, top, archMaskImage), name);
        archMaskImage.close();

        side.close();
        top.close();
    }

    private void register(String kind, NativeImage image, String name) {
        var old = this.generated.get(kind);
        if (old != null) {
            LBGData.removeImage(old);
        }

        this.images.put(kind, image);
        this.generated.put(kind, LBGData.addImage(name + "_" + this.index + "_" + kind, image));
    }

    public void bakeTextures(Function<Material, TextureAtlasSprite> spriteGetter) {
        for (var entry : this.overrides.entrySet()) {
            this.bakedSprites.put(entry.getKey(), spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, entry.getValue())));
        }

        for (var entry : this.generated.entrySet()) {
            this.bakedSprites.put(entry.getKey(), spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, entry.getValue())));
        }
    }

    public static LBGLayer mergeLayers(LBGLayer parent, LBGLayer child) {
        if (parent.colorIndex != child.colorIndex) {
            return parent;
        }

        var name = textureName(parent.parentMetadata.id);
        for (String kind : TEXTURE_KINDS) {
            var parentImage = parent.images.get(kind);
            var childImage = child.images.get(kind);
            if (parentImage == null || childImage == null) {
                continue;
            }

            parent.register(kind, LBGTextureGenerator.applyMask(parentImage, childImage, childImage), name);
        }

        return parent;
    }

    private static String textureName(ResourceLocation id) {
        var path = id.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static ResourceLocation getTexturePath(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }
}
