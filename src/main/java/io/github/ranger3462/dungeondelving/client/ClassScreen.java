package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.character.ClassPayloads;
import io.github.ranger3462.dungeondelving.character.PlayerClass;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The class screen (DD-83), opened by reading a Tome of Calling: classes on the left, the selected one's tree and
 * passive bonus on the right, and a button to become it (which uses up the tome). Closes with Esc, keeping the tome.
 */
public class ClassScreen extends Screen {
    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("popup/background");
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/deepslate_tiles.png");
    private static final int WIDTH = 320;
    private static final int HEIGHT = 170;
    private static final int LIST_WIDTH = 90;

    private final PlayerClass current;
    private PlayerClass selected;

    private ClassScreen(PlayerClass current) {
        super(Component.translatable(current == null ? "screen.dungeondelving.class" : "screen.dungeondelving.class.change"));
        this.current = current;
        this.selected = current == null ? PlayerClass.FIGHTER : current;
    }

    public static void open(ClassPayloads.Open payload) {
        Minecraft.getInstance().setScreen(new ClassScreen(PlayerClass.byId(payload.current()).orElse(null)));
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
        for (PlayerClass playerClass : PlayerClass.values()) {
            addRenderableWidget(Button.builder(playerClass.displayName(), button -> {
                selected = playerClass;
                rebuildWidgets();
            }).bounds(left() + 8, y, LIST_WIDTH - 8, 20).build()).active = playerClass != selected;
            y += 21;
        }
        Button choose = addRenderableWidget(Button.builder(Component.translatable("screen.dungeondelving.class.choose", selected.displayName()), button -> {
            PacketDistributor.sendToServer(new ClassPayloads.Choose(selected.id));
            onClose();
        }).bounds(left() + LIST_WIDTH + 8, top() + HEIGHT - 28, WIDTH - LIST_WIDTH - 16, 20).build());
        choose.active = selected != current;
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
        for (FormattedCharSequence line : font.split(selected.description(), WIDTH - LIST_WIDTH - 20)) {
            graphics.drawString(font, line, x, y, 0xD0D0D0);
            y += 10;
        }
        if (current != null) {
            y += 6;
            for (FormattedCharSequence line : font.split(Component.translatable("screen.dungeondelving.class.warning").withStyle(ChatFormatting.GOLD),
                    WIDTH - LIST_WIDTH - 20)) {
                graphics.drawString(font, line, x, y, 0xFFFFFF);
                y += 10;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
