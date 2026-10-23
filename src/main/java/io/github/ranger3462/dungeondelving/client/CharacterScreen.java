package io.github.ranger3462.dungeondelving.client;

import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.AbilityScore;
import io.github.ranger3462.dungeondelving.character.AllocateScorePayload;
import io.github.ranger3462.dungeondelving.character.CharacterSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The stats screen (DD-80): the character level, unspent ability points, and each ability score with a button to
 * put a point into it. Opened with its key (J by default). Scores come straight from the player's synced attributes;
 * the level and points come from the server ({@link CharacterSyncPayload}).
 */
public class CharacterScreen extends Screen {
    public static final KeyMapping OPEN = new KeyMapping("key.dungeondelving.character", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.dungeondelving");
    private static final int ROW_HEIGHT = 22;
    private static final int WIDTH = 220;

    /** The latest character info from the server. */
    private static CharacterSyncPayload latest = new CharacterSyncPayload(1, 0, Map.of());

    public CharacterScreen() {
        super(Component.translatable("screen.dungeondelving.character"));
    }

    public static void receive(CharacterSyncPayload payload) {
        latest = payload;
        if (Minecraft.getInstance().screen instanceof CharacterScreen screen) {
            screen.rebuildWidgets();
        }
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return height / 2 - (AbilityScore.values().length * ROW_HEIGHT) / 2 - 10;
    }

    @Override
    protected void init() {
        if (latest.available() <= 0) {
            return;
        }
        int y = top();
        for (AbilityScore score : AbilityScore.values()) {
            addRenderableWidget(Button.builder(Component.literal("+"), button -> PacketDistributor.sendToServer(new AllocateScorePayload(score.id)))
                    .bounds(left() + WIDTH - 20, y, 20, 20).build());
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int y = top();
        graphics.drawCenteredString(font, Component.translatable("screen.dungeondelving.character.level", latest.level()), width / 2, y - 34, 0xFFE0A0);
        graphics.drawCenteredString(font, Component.translatable("screen.dungeondelving.character.points", latest.available()), width / 2, y - 20,
                latest.available() > 0 ? 0x80FF80 : 0xA0A0A0);
        for (AbilityScore score : AbilityScore.values()) {
            AttributeInstance attribute = minecraft.player == null ? null : minecraft.player.getAttribute(score.attribute());
            int value = attribute == null ? (int) AbilityScore.BASE : (int) Math.round(attribute.getValue());
            int allocated = latest.allocated().getOrDefault(score.id, 0);
            graphics.drawString(font, Component.literal(score.abbreviation).withStyle(ChatFormatting.BOLD), left, y + 6, 0xFFFFFF);
            graphics.drawString(font, score.displayName(), left + 34, y + 6, 0xC0C0C0);
            graphics.drawString(font, Component.literal(String.valueOf(value)), left + 130, y + 6, 0xFFFFFF);
            if (allocated > 0) {
                graphics.drawString(font, Component.literal("(+" + allocated + ")"), left + 152, y + 6, 0x80C080);
            }
            if (mouseX >= left && mouseX < left + 150 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                graphics.renderTooltip(font, Component.translatable("attribute.dungeondelving." + score.id + ".description"), mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }
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
            event.register(OPEN);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (minecraft.screen == null && minecraft.player != null) {
                    minecraft.setScreen(new CharacterScreen());
                }
            }
        }
    }
}
