package com.sakuracha.opengrass.client.metadata;

import com.google.gson.JsonObject;
import com.sakuracha.opengrass.client.model.LBGModelBaker;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Represents the metadata of a layer type for a block.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class LBGLayerMetadata {
    private final LBGLayerType layerType;
    private final StateDefinition<Block, BlockState> definition;
    private final boolean layerModel;
    private final @Nullable Vector3f offset;
    private final @Nullable JsonObject blockStateJson;
    private final Map<String, BakedModel> alternateModels = new HashMap<>();
    private @Nullable BlockModelDefinition parsedDefinition;
    private @Nullable BakedModel bakedLayerModel;
    private boolean layerModelBaked;

    public LBGLayerMetadata(LBGLayerType layerType, StateDefinition<Block, BlockState> definition, JsonObject json) {
        this.layerType = layerType;
        this.definition = definition;
        this.layerModel = json.has("layer") && json.get("layer").getAsBoolean();

        if (json.has("offset")) {
            var array = json.getAsJsonArray("offset");
            this.offset = new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
        } else {
            this.offset = null;
        }

        this.blockStateJson = json.has("block_state") ? json.getAsJsonObject("block_state") : null;
    }

    public LBGLayerType layerType() {
        return this.layerType;
    }

    public boolean hasLayerModel() {
        return this.layerModel;
    }

    public @Nullable Vector3f offset() {
        return this.offset;
    }

    public @Nullable LBGCompiledLayerMetadata bake(LBGModelBaker baker, Function<Material, TextureAtlasSprite> sprites, String variant) {
        BakedModel layer = null;
        if (this.layerModel) {
            if (!this.layerModelBaked) {
                var legacyModelId = this.layerType.legacyModelId();
                this.bakedLayerModel = legacyModelId != null
                        ? baker.bake(legacyModelId, BlockModelRotation.X0_Y0, sprites)
                        : baker.bakeState(this.layerType.displayState());
                this.layerModelBaked = true;
            }
            layer = this.bakedLayerModel;
        }

        BakedModel alternate = null;
        if (this.blockStateJson != null) {
            if (!this.alternateModels.containsKey(variant)) {
                var unbaked = this.getAlternateUnbaked(baker, variant);
                this.alternateModels.put(variant, unbaked == null ? null : baker.bakeUncached(unbaked, BlockModelRotation.X0_Y0, sprites));
            }
            alternate = this.alternateModels.get(variant);
        }

        if (layer == null && alternate == null) {
            return null;
        }

        return new LBGCompiledLayerMetadata(this.layerType, this.offset, layer, alternate);
    }

    private @Nullable UnbakedModel getAlternateUnbaked(LBGModelBaker baker, String variant) {
        if (this.blockStateJson == null) {
            return null;
        }

        if (this.parsedDefinition == null) {
            var context = new BlockModelDefinition.Context();
            context.setDefinition(this.definition);
            this.parsedDefinition = BlockModelDefinition.fromJsonElement(context, this.blockStateJson);
        }

        UnbakedModel model;
        if (this.parsedDefinition.isMultiPart()) {
            model = this.parsedDefinition.getMultiPart();
        } else {
            model = this.parsedDefinition.getVariants().get(variant);
            if (model == null && !this.parsedDefinition.getVariants().isEmpty()) {
                model = this.parsedDefinition.getVariants().values().iterator().next();
            }
        }

        if (model != null) {
            model.resolveParents(baker::getModel);
        }

        return model;
    }
}
