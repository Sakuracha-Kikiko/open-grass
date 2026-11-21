package com.sakuracha.opengrass.client.resource;

import com.sakuracha.opengrass.client.LBGData;
import com.sakuracha.opengrass.client.event.LBGClientEvents;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;

/**
 * A sprite source which generates the better grass textures and registers them into the block atlas.
 *
 * <p>This is also where the better grass data files are loaded since the atlas stitching phase
 * happens before the models are baked.</p>
 *
 * @author Sakuracha Kikiko
 */
public final class LBGSpriteSource implements SpriteSource {
    public static final LBGSpriteSource INSTANCE = new LBGSpriteSource();

    private LBGSpriteSource() {
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        LBGData.reload(resourceManager);

        for (var entry : LBGData.images().entrySet()) {
            ResourceLocation id = entry.getKey();
            var image = entry.getValue();
            output.add(id, spriteLoader -> new SpriteContents(id, new FrameSize(image.getWidth(), image.getHeight()), image, ResourceMetadata.EMPTY));
        }
    }

    @Override
    public SpriteSourceType type() {
        return LBGClientEvents.spriteSourceType();
    }
}
