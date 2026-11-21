package com.sakuracha.opengrass.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Utilities to edit the quads of a baked model, since {@link BakedQuad} is immutable.
 *
 * <p>The quads are assumed to use the {@code minecraft:block} vertex format, which stores the position at the
 * first three integers, the UV at the fourth and fifth, and the normal at the last one.</p>
 *
 * @author Sakuracha Kikiko
 */
public final class LBGQuadUtil {
    private static final int UV_OFFSET = 4;

    private LBGQuadUtil() {
    }

    /**
     * Replaces the sprite of the given quad, remapping the UVs of the quad to the new sprite.
     *
     * @param quad the quad
     * @param sprite the new sprite
     * @return the edited quad, or the original quad if the sprite is {@code null} or already the one of the quad
     */
    public static BakedQuad withSprite(BakedQuad quad, @Nullable TextureAtlasSprite sprite) {
        var old = quad.getSprite();
        if (sprite == null || sprite == old) {
            return quad;
        }

        var vertices = quad.getVertices();
        var stride = vertices.length / 4;
        var result = vertices.clone();
        float u0 = old.getU0();
        float u1 = old.getU1();
        float v0 = old.getV0();
        float v1 = old.getV1();
        float uSpan = u1 - u0;
        float vSpan = v1 - v0;

        for (int i = 0; i < 4; i++) {
            var offset = i * stride;
            float u = Float.intBitsToFloat(vertices[offset + UV_OFFSET]);
            float v = Float.intBitsToFloat(vertices[offset + UV_OFFSET + 1]);
            float localU = uSpan == 0.0F ? 0.0F : (u - u0) / uSpan;
            float localV = vSpan == 0.0F ? 0.0F : (v - v0) / vSpan;
            result[offset + UV_OFFSET] = Float.floatToRawIntBits(sprite.getU(localU));
            result[offset + UV_OFFSET + 1] = Float.floatToRawIntBits(sprite.getV(localV));
        }

        return new BakedQuad(result, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade(),
                quad.hasAmbientOcclusion());
    }

    /**
     * Translates the given quads.
     *
     * @param quads the quads
     * @param offset the translation, may be {@code null}
     * @return the translated quads
     */
    public static List<BakedQuad> translate(List<BakedQuad> quads, @Nullable Vector3f offset) {
        if (offset == null || (offset.x == 0.0F && offset.y == 0.0F && offset.z == 0.0F)) {
            return quads;
        }

        var result = new ArrayList<BakedQuad>(quads.size());
        for (var quad : quads) {
            result.add(translate(quad, offset.x, offset.y, offset.z, false));
        }
        return result;
    }

    /**
     * Prepares the quads of a layer model, undoing the model offset of the block and disabling the ambient occlusion.
     *
     * @param quads the quads of the layer model
     * @param modelOffset the model offset of the block
     * @return the prepared quads
     */
    public static List<BakedQuad> prepareLayerQuads(List<BakedQuad> quads, Vec3 modelOffset) {
        if (modelOffset.x == 0.0D && modelOffset.y == 0.0D && modelOffset.z == 0.0D) {
            return quads;
        }

        var result = new ArrayList<BakedQuad>(quads.size());
        for (var quad : quads) {
            result.add(translate(quad, (float) -modelOffset.x, (float) -modelOffset.y, (float) -modelOffset.z, true));
        }
        return result;
    }

    private static BakedQuad translate(BakedQuad quad, float x, float y, float z, boolean disableAmbientOcclusion) {
        var vertices = quad.getVertices();
        var stride = vertices.length / 4;
        var result = vertices.clone();

        for (int i = 0; i < 4; i++) {
            var offset = i * stride;
            result[offset] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset]) + x);
            result[offset + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 1]) + y);
            result[offset + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 2]) + z);
        }

        return new BakedQuad(result, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                disableAmbientOcclusion ? false : quad.hasAmbientOcclusion());
    }
}
