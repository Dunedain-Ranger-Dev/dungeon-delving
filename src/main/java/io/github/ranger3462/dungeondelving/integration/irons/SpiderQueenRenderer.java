package io.github.ranger3462.dungeondelving.integration.irons;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.redspace.ironsspellbooks.entity.mobs.ice_spider.IceSpiderEntity;
import io.redspace.ironsspellbooks.entity.mobs.ice_spider.IceSpiderRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Draws the spider queens with Iron's Spells' ice spider model and animations, recolored to their kind: spider browns,
 * cave spider teals, or the ice spider's own colors for the Ice Spider Queen. Iron's Spells is All Rights Reserved, so
 * nothing of its art is copied into this mod: the recolored textures are made in memory from Iron's own texture each
 * time resources load. Client only; registered only when Iron's Spells is installed.
 */
public class SpiderQueenRenderer extends IceSpiderRenderer {
    private static final ResourceLocation ICE_SPIDER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/entity/ice_spider/ice_spider.png");
    public static final ResourceLocation SPIDER = DungeonDelving.id("dynamic/spider_queen");
    public static final ResourceLocation CAVE_SPIDER = DungeonDelving.id("dynamic/cave_spider_queen");

    /** A recoloring: the body's darkest and lightest shades, the crystals', and the eyes. Colors are 0xRRGGBB. */
    private record Palette(int bodyDark, int bodyLight, int crystalDark, int crystalLight, int eye) {}

    private static final Palette SPIDER_PALETTE = new Palette(0x181310, 0x463A32, 0x221A16, 0x5C4A3E, 0xC81414);
    private static final Palette CAVE_SPIDER_PALETTE = new Palette(0x081E24, 0x1E5058, 0x0C2E38, 0x287680, 0xC81414);

    private final ResourceLocation texture;

    public SpiderQueenRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(IceSpiderEntity animatable) {
        return texture == null ? super.getTextureLocation(animatable) : texture;
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IronsSpiders.SPIDER_QUEEN.get(), context -> new SpiderQueenRenderer(context, SPIDER));
        event.registerEntityRenderer(IronsSpiders.CAVE_SPIDER_QUEEN.get(), context -> new SpiderQueenRenderer(context, CAVE_SPIDER));
        event.registerEntityRenderer(IronsSpiders.ICE_SPIDER_QUEEN.get(), context -> new SpiderQueenRenderer(context, null));
    }

    /** Remakes the recolored textures whenever resources load. */
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) SpiderQueenRenderer::makeTextures);
    }

    private static void makeTextures(ResourceManager resources) {
        Optional<Resource> source = resources.getResource(ICE_SPIDER);
        if (source.isEmpty()) {
            return;
        }
        try (InputStream in = source.get().open(); NativeImage ice = NativeImage.read(in)) {
            Minecraft.getInstance().getTextureManager().register(SPIDER, new DynamicTexture(recolor(ice, SPIDER_PALETTE)));
            Minecraft.getInstance().getTextureManager().register(CAVE_SPIDER, new DynamicTexture(recolor(ice, CAVE_SPIDER_PALETTE)));
        } catch (IOException e) {
            DungeonDelving.LOGGER.warn("Couldn't recolor the ice spider for the spider queens", e);
        }
    }

    /**
     * A recolored copy of the ice spider's texture: its darkest pixels (the eyes) become eye-colored, its blue crystals
     * the crystal shades and the rest the body shades, each keeping its lightness.
     */
    static NativeImage recolor(NativeImage ice, Palette palette) {
        NativeImage out = new NativeImage(ice.getWidth(), ice.getHeight(), true);
        for (int y = 0; y < ice.getHeight(); y++) {
            for (int x = 0; x < ice.getWidth(); x++) {
                int abgr = ice.getPixelRGBA(x, y);
                int alpha = abgr >>> 24;
                if (alpha == 0) {
                    out.setPixelRGBA(x, y, 0);
                    continue;
                }
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                float max = Math.max(r, Math.max(g, b)) / 255f;
                float min = Math.min(r, Math.min(g, b)) / 255f;
                float lightness = (max + min) / 2;
                float saturation = max == min ? 0 : (max - min) / (lightness > 0.5f ? 2 - max - min : max + min);
                int rgb;
                if (lightness < 0.25f) {
                    rgb = palette.eye();
                } else if (saturation > 0.45f && b > r && b >= g) {
                    rgb = mix(palette.crystalDark(), palette.crystalLight(), (lightness - 0.35f) / 0.6f);
                } else {
                    rgb = mix(palette.bodyDark(), palette.bodyLight(), (lightness - 0.45f) / 0.55f);
                }
                out.setPixelRGBA(x, y, alpha << 24 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
            }
        }
        return out;
    }

    private static int mix(int from, int to, float t) {
        float amount = Math.max(0, Math.min(1, t));
        int r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return r << 16 | g << 8 | b;
    }
}
