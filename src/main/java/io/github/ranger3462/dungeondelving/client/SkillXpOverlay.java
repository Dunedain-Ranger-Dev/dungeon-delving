package io.github.ranger3462.dungeondelving.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.SkillGroup;
import io.github.ranger3462.dungeondelving.character.SkillXpPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The skill XP popups: each skill that gains experience gets a row right of the hotbar with its icon, a bar like
 * vanilla's XP bar showing progress to its next level, and the experience gained. Rows stack upward, newest at the
 * bottom; each shows for a few seconds after its last gain, then fades. Gains in a skill already showing add up.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class SkillXpOverlay {
    /** How long a row shows after its last gain, then how long it takes to fade, in milliseconds. */
    private static final long SHOW_MS = 3000;
    private static final long FADE_MS = 600;
    private static final int BAR_WIDTH = 80;
    private static final int ROW_HEIGHT = 12;
    /** Clear of the hotbar and Combat Roll's roll icon beside it. */
    private static final int LEFT_OF_HOTBAR = 91 + 34;
    private static final int MAX_ROWS = 6;
    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation BAR_PROGRESS = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    /** The vanilla XP bar sprites' size. */
    private static final int SPRITE_WIDTH = 182;
    private static final int SPRITE_HEIGHT = 5;

    /** One skill's row. */
    private static final class Row {
        final ResourceLocation skill;
        SkillXpPayload latest;
        int gained;
        long shownAt;

        Row(ResourceLocation skill) {
            this.skill = skill;
        }
    }

    private static final List<Row> ROWS = new ArrayList<>();

    private SkillXpOverlay() {}

    public static void receive(SkillXpPayload payload) {
        ResourceLocation skill = ResourceLocation.parse(payload.skill());
        long now = System.currentTimeMillis();
        Row row = ROWS.stream().filter(r -> r.skill.equals(skill)).findFirst().orElse(null);
        if (row == null || now - row.shownAt > SHOW_MS + FADE_MS) {
            ROWS.remove(row);
            row = new Row(skill);
            ROWS.add(row);
        } else if (now - row.shownAt > SHOW_MS) {
            row.gained = 0; // It had started fading: start a fresh count.
        }
        row.latest = payload;
        row.gained += payload.gained();
        row.shownAt = now;
        while (ROWS.size() > MAX_ROWS) {
            ROWS.removeFirst();
        }
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, DungeonDelving.id("skill_xp"), SkillXpOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        ROWS.removeIf(row -> now - row.shownAt > SHOW_MS + FADE_MS);
        if (ROWS.isEmpty() || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        int x = graphics.guiWidth() / 2 + LEFT_OF_HOTBAR;
        int bottom = graphics.guiHeight() - 15;
        RenderSystem.enableBlend();
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(ROWS.size() - 1 - i); // Newest at the bottom.
            long age = now - row.shownAt;
            float alpha = age <= SHOW_MS ? 1.0f : 1.0f - (age - SHOW_MS) / (float) FADE_MS;
            int y = bottom - i * ROW_HEIGHT;

            graphics.pose().pushPose();
            graphics.pose().translate(x, y - 4, 0);
            graphics.pose().scale(0.75f, 0.75f, 1.0f);
            graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(SkillGroup.icon(row.skill))), 0, 0);
            graphics.pose().popPose();

            int barX = x + 15;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            // The left part of vanilla's XP bar, then its rounded end, so a shorter bar still looks like it.
            graphics.blitSprite(BAR_BACKGROUND, SPRITE_WIDTH, SPRITE_HEIGHT, 0, 0, barX, y, BAR_WIDTH - 1, SPRITE_HEIGHT);
            graphics.blitSprite(BAR_BACKGROUND, SPRITE_WIDTH, SPRITE_HEIGHT, SPRITE_WIDTH - 1, 0, barX + BAR_WIDTH - 1, y, 1, SPRITE_HEIGHT);
            float progress = row.latest.required() <= 0 ? 1.0f : Mth.clamp(row.latest.current() / (float) row.latest.required(), 0.0f, 1.0f);
            int filled = Math.round(BAR_WIDTH * progress);
            if (filled > 0) {
                graphics.blitSprite(BAR_PROGRESS, SPRITE_WIDTH, SPRITE_HEIGHT, 0, 0, barX, y, filled, SPRITE_HEIGHT);
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            int a = Mth.clamp((int) (alpha * 255), 8, 255) << 24;
            graphics.drawString(minecraft.font, "+" + row.gained, barX + BAR_WIDTH + 4, y - 2, 0x80FF20 | a);
        }
        RenderSystem.disableBlend();
    }
}
