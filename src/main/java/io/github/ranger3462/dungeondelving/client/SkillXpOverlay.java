package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.SkillGroup;
import io.github.ranger3462.dungeondelving.character.SkillXpPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The skill XP popup: when a skill gains experience, its icon, name, level and a progress bar show just right of the
 * hotbar for a few seconds, then fade. A new gain replaces it, and gains in the same skill add up while it's shown.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class SkillXpOverlay {
    /** How long it shows after the last gain, then how long it takes to fade, in milliseconds. */
    private static final long SHOW_MS = 3000;
    private static final long FADE_MS = 600;
    private static final int BAR_WIDTH = 72;

    private static SkillXpPayload latest;
    private static int stackedGain;
    private static long shownAt;

    private SkillXpOverlay() {}

    public static void receive(SkillXpPayload payload) {
        boolean sameSkill = latest != null && latest.skill().equals(payload.skill()) && System.currentTimeMillis() - shownAt < SHOW_MS;
        stackedGain = (sameSkill ? stackedGain : 0) + payload.gained();
        latest = payload;
        shownAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, DungeonDelving.id("skill_xp"), SkillXpOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (latest == null || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MS + FADE_MS) {
            return;
        }
        float alpha = age <= SHOW_MS ? 1.0f : 1.0f - (age - SHOW_MS) / (float) FADE_MS;
        int a = Mth.clamp((int) (alpha * 255), 8, 255) << 24;
        ResourceLocation skill = ResourceLocation.parse(latest.skill());
        int x = graphics.guiWidth() / 2 + 91 + 8;
        int y = graphics.guiHeight() - 21;

        graphics.fill(x - 2, y - 2, x + 20 + BAR_WIDTH + 4, y + 20, (int) (alpha * 0x90) << 24);
        graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(SkillGroup.icon(skill))), x, y);
        var font = minecraft.font;
        Component label = SkillGroup.name(skill).copy().append(" " + latest.level());
        graphics.drawString(font, label, x + 20, y, 0xFFFFFF | a);
        String gain = "+" + stackedGain;
        graphics.drawString(font, gain, x + 20 + BAR_WIDTH - font.width(gain), y, 0x80FF80 | a);
        // The progress bar toward the next level.
        int barY = y + 11;
        float progress = latest.required() <= 0 ? 1.0f : Mth.clamp(latest.current() / (float) latest.required(), 0.0f, 1.0f);
        graphics.fill(x + 20, barY, x + 20 + BAR_WIDTH, barY + 4, 0x404040 | a);
        graphics.fill(x + 20, barY, x + 20 + Math.round(BAR_WIDTH * progress), barY + 4, 0x40C0FF | a);
    }
}
