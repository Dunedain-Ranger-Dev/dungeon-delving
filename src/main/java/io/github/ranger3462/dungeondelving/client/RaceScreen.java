package io.github.ranger3462.dungeondelving.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.AbilityScore;
import io.github.ranger3462.dungeondelving.character.Race;
import io.github.ranger3462.dungeondelving.character.RacePayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The race screen (DD-82): shown on first join (and after an Elixir of Rebirth) until a race is picked. Races on the
 * left; the selected one's ability bonuses and traits on the right; Choose to confirm. It can't be closed without
 * picking. Also holds the racial ability key (H by default).
 */
public class RaceScreen extends Screen {
    public static final KeyMapping ABILITY = new KeyMapping("key.dungeondelving.racial_ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.dungeondelving");
    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("popup/background");
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/deepslate_tiles.png");
    private static final int WIDTH = 320;
    private static final int HEIGHT = 200;
    private static final int LIST_WIDTH = 90;

    private Race selected = Race.HUMAN;

    public RaceScreen() {
        super(Component.translatable("screen.dungeondelving.race"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new RaceScreen());
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    protected void init() {
        int y = top() + 22;
        for (Race race : Race.values()) {
            addRenderableWidget(Button.builder(race.displayName(), button -> {
                selected = race;
                rebuildWidgets();
            }).bounds(left() + 8, y, LIST_WIDTH - 8, 20).build()).active = race != selected;
            y += 21;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.dungeondelving.race.choose", selected.displayName()), button -> {
            PacketDistributor.sendToServer(new RacePayloads.Choose(selected.id));
            onClose();
        }).bounds(left() + LIST_WIDTH + 8, top() + HEIGHT - 28, WIDTH - LIST_WIDTH - 16, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int x0 = left();
        int y0 = top();
        graphics.blitSprite(FRAME, x0 - 6, y0 - 6, WIDTH + 12, HEIGHT + 12);
        graphics.blit(BACKGROUND, x0, y0, 0, 0.0f, 0.0f, WIDTH, HEIGHT, 16, 16);
        graphics.fill(x0, y0, x0 + WIDTH, y0 + HEIGHT, 0xB8000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top() + 8, 0xFFE0A0);
        int x = left() + LIST_WIDTH + 10;
        int y = top() + 24;
        graphics.drawString(font, selected.displayName().copy().withStyle(ChatFormatting.BOLD), x, y, 0xFFFFFF);
        y += 14;
        MutableComponent scores = Component.empty();
        for (AbilityScore score : AbilityScore.values()) {
            int bonus = selected.bonus(score);
            if (bonus != 0) {
                if (!scores.getSiblings().isEmpty()) {
                    scores.append("  ");
                }
                scores.append(Component.literal("+" + bonus + " " + score.abbreviation).withStyle(ChatFormatting.AQUA));
            }
        }
        // Wrapped: a human's six bonuses don't fit on one line.
        for (FormattedCharSequence line : font.split(scores, WIDTH - LIST_WIDTH - 20)) {
            graphics.drawString(font, line, x, y, 0xFFFFFF);
            y += 10;
        }
        y += 4;
        if (selected.scale != 0) {
            String size = selected.scale < 0 ? "Smaller" : "Larger";
            graphics.drawString(font, Component.literal(size + " (" + Math.round((1 + selected.scale) * 100) + "% size)").withStyle(ChatFormatting.GRAY), x, y,
                    0xFFFFFF);
            y += 14;
        }
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(selected.description(), WIDTH - LIST_WIDTH - 20));
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, 0xD0D0D0);
            y += 10;
        }
    }

    /** It stays until a race is picked. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(ABILITY);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (ABILITY.consumeClick()) {
                if (Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) {
                    PacketDistributor.sendToServer(new RacePayloads.UseAbility());
                }
            }
        }
    }
}
