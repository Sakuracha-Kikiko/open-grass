package com.sakuracha.opengrass.client.model;

import com.sakuracha.opengrass.OpenGrass;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A minimal model baker which loads the models it needs directly from the resource manager.
 *
 * @author Sakuracha Kikiko
 */
public final class LBGModelBaker implements ModelBaker {
    private final ResourceManager resourceManager;
    private final java.util.function.Function<Material, TextureAtlasSprite> sprites;
    private final @Nullable Map<ModelResourceLocation, BakedModel> vanillaModels;
    private final Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
    private final Map<CacheKey, BakedModel> bakedModels = new HashMap<>();
    private final Set<ResourceLocation> loading = new HashSet<>();

    public LBGModelBaker(ResourceManager resourceManager, java.util.function.Function<Material, TextureAtlasSprite> sprites) {
        this(resourceManager, sprites, null);
    }

    public LBGModelBaker(ResourceManager resourceManager, java.util.function.Function<Material, TextureAtlasSprite> sprites,
            @Nullable Map<ModelResourceLocation, BakedModel> vanillaModels) {
        this.resourceManager = resourceManager;
        this.sprites = sprites;
        this.vanillaModels = vanillaModels;
    }

    /**
     * Returns the already baked model of the given block state.
     *
     * <p>Layer types render a block state of the game, so the model of that state is already baked by the model
     * bakery when the baking result is modified. Reusing it keeps the layer in sync with resource packs and avoids
     * baking a model a second time.</p>
     *
     * @param state the block state
     * @return the baked model, or {@code null} if it is not available
     */
    public @Nullable BakedModel bakeState(BlockState state) {
        if (this.vanillaModels == null) {
            return null;
        }

        var location = BlockModelShaper.stateToModelLocation(state);
        var model = this.vanillaModels.get(location);
        if (model == null) {
            OpenGrass.LOGGER.warn("Missing baked model for the layer display state {}.", location);
        }

        return model;
    }

    @Override
    public UnbakedModel getModel(ResourceLocation location) {
        var cached = this.models.get(location);
        if (cached != null) {
            return cached;
        }

        if (!this.loading.add(location)) {
            throw new IllegalStateException("Circular model dependency while loading " + location);
        }

        try {
            var file = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "models/" + location.getPath() + ".json");
            var resource = this.resourceManager.getResource(file)
                    .orElseThrow(() -> new IllegalStateException("Missing model " + location));
            BlockModel model;
            try (var reader = resource.openAsReader()) {
                model = BlockModel.fromStream(reader);
            }

            this.models.put(location, model);
            model.resolveParents(this::getModel);
            return model;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model " + location, e);
        } finally {
            this.loading.remove(location);
        }
    }

    @Override
    public BakedModel bake(ResourceLocation location, ModelState state, java.util.function.Function<Material, TextureAtlasSprite> textureGetter) {
        var key = new CacheKey(location, state);
        var cached = this.bakedModels.get(key);
        if (cached != null) {
            return cached;
        }

        var model = this.bakeUncached(this.getModel(location), state, textureGetter);
        this.bakedModels.put(key, model);
        return model;
    }

    @Override
    public @Nullable BakedModel bake(ResourceLocation location, ModelState state) {
        return this.bake(location, state, this.sprites);
    }

    @Override
    public BakedModel bakeUncached(UnbakedModel model, ModelState state, java.util.function.Function<Material, TextureAtlasSprite> textureGetter) {
        return model.bake(this, textureGetter, state);
    }

    @Override
    public @Nullable UnbakedModel getTopLevelModel(ModelResourceLocation modelLocation) {
        return null;
    }

    @Override
    public java.util.function.Function<Material, TextureAtlasSprite> getModelTextureGetter() {
        return this.sprites;
    }

    private record CacheKey(ResourceLocation location, ModelState state) {
    }
}
