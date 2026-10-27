package io.github.ranger3462.dungeondelving.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.realm.RealmMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The realm menu (K by default): a map of the realm's places in your dimension, coloured by owner, with their links
 * (red where they cross a border) and a red "!" on any in trouble; and a side panel with how the kingdom and each
 * nation stand, the raids under way, and either the places in trouble or the one you've clicked. Drag to move the map,
 * scroll to zoom. The data comes from the server ({@link RealmMap}).
 */
public class RealmScreen extends Screen {
    public static final KeyMapping OPEN = new KeyMapping("key.dungeondelving.realm", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.dungeondelving");
    private static final int PANEL_WIDTH = 200;
    private static final int MARGIN = 8;
    private static final int TOP = 22;
    private static final int LINE = 10;
    private static final int GOLD = 0xFFE0A0;
    private static final int GREY = 0xA0A0A0;
    private static final int RED = 0xFF6060;
    private static final int YELLOW = 0xF0D060;

    /** The map's view: the world block at the middle of the map area, and pixels per block. */
    private double centerX;
    private double centerZ;
    private double scale = -1;
    private String shownDim;
    private String selected;
    private boolean dragged;
    /** The panel's clickable rows this frame: a node id each, by top y. */
    private final List<Object[]> rows = new ArrayList<>();

    public RealmScreen() {
        super(Component.translatable("screen.dungeondelving.realm"));
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(new RealmMap.Request());
        if (shownDim == null && minecraft.level != null) {
            shownDim = minecraft.level.dimension().location().toString();
        }
    }

    private int mapLeft() {
        return MARGIN;
    }

    private int mapRight() {
        return width - PANEL_WIDTH - MARGIN * 2;
    }

    private int mapTop() {
        return TOP;
    }

    private int mapBottom() {
        return height - MARGIN - 12;
    }

    private List<RealmMapClient.Node> shown(RealmMapClient.Data data) {
        return data.nodes().values().stream().filter(node -> node.dim().equals(shownDim)).toList();
    }

    /** Fits the view to the shown places (and the player, if they're in this dimension). */
    private void fit(RealmMapClient.Data data) {
        List<RealmMapClient.Node> nodes = shown(data);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (RealmMapClient.Node node : nodes) {
            minX = Math.min(minX, node.x());
            maxX = Math.max(maxX, node.x());
            minZ = Math.min(minZ, node.z());
            maxZ = Math.max(maxZ, node.z());
        }
        if (minecraft.player != null && minecraft.level != null && minecraft.level.dimension().location().toString().equals(shownDim)) {
            minX = Math.min(minX, minecraft.player.getX());
            maxX = Math.max(maxX, minecraft.player.getX());
            minZ = Math.min(minZ, minecraft.player.getZ());
            maxZ = Math.max(maxZ, minecraft.player.getZ());
        }
        if (minX == Double.MAX_VALUE) {
            centerX = 0;
            centerZ = 0;
            scale = 0.25;
            return;
        }
        centerX = (minX + maxX) / 2;
        centerZ = (minZ + maxZ) / 2;
        double spanX = Math.max(64, maxX - minX);
        double spanZ = Math.max(64, maxZ - minZ);
        scale = Math.min((mapRight() - mapLeft() - 60) / spanX, (mapBottom() - mapTop() - 40) / spanZ);
    }

    private int screenX(double x) {
        return (int) Math.round((mapLeft() + mapRight()) / 2.0 + (x - centerX) * scale);
    }

    private int screenY(double z) {
        return (int) Math.round((mapTop() + mapBottom()) / 2.0 + (z - centerZ) * scale);
    }

    private static int size(RealmMapClient.Node node) {
        return switch (node.type()) {
            case "capital" -> 11;
            case "town", "stronghold" -> 8;
            case "site" -> 6;
            default -> 5;
        };
    }

    private RealmMapClient.Node nodeAt(RealmMapClient.Data data, double mouseX, double mouseY) {
        RealmMapClient.Node best = null;
        double bestDistance = 8 * 8;
        for (RealmMapClient.Node node : shown(data)) {
            double dx = screenX(node.x()) - mouseX;
            double dy = screenY(node.z()) - mouseY;
            if (dx * dx + dy * dy <= bestDistance) {
                best = node;
                bestDistance = dx * dx + dy * dy;
            }
        }
        return best;
    }

    private boolean inMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft() && mouseX < mapRight() && mouseY >= mapTop() && mouseY < mapBottom();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, MARGIN, 8, GOLD);
        RealmMapClient.Data data = RealmMapClient.latest();
        if (data == null) {
            graphics.drawCenteredString(font, Component.literal("Waiting for the realm..."), width / 2, height / 2, GREY);
            return;
        }
        if (scale < 0) {
            fit(data);
        }
        renderMap(graphics, data, mouseX, mouseY);
        renderPanel(graphics, data, mouseX, mouseY);
    }

    private void renderMap(GuiGraphics graphics, RealmMapClient.Data data, int mouseX, int mouseY) {
        int x0 = mapLeft(), y0 = mapTop(), x1 = mapRight(), y1 = mapBottom();
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF505050);
        graphics.fill(x0, y0, x1, y1, 0xF0141A20);
        graphics.drawString(font, Component.literal(dimName(shownDim)), x0 + 4, y0 + 4, GREY);
        graphics.enableScissor(x0, y0, x1, y1);
        for (String[] link : data.links()) {
            RealmMapClient.Node a = data.nodes().get(link[0]);
            RealmMapClient.Node b = data.nodes().get(link[1]);
            if (a == null || b == null || !a.dim().equals(shownDim) || !b.dim().equals(shownDim)) {
                continue;
            }
            boolean border = (a.kingdoms() && b.monsters()) || (a.monsters() && b.kingdoms());
            line(graphics, screenX(a.x()), screenY(a.z()), screenX(b.x()), screenY(b.z()), border ? 0xFFC04040 : 0xFF606870);
        }
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        for (RealmMapClient.Node node : shown(data)) {
            int sx = screenX(node.x()), sy = screenY(node.z()), half = size(node) / 2;
            int outline = node.id().equals(selected) ? 0xFFFFFFFF : "capital".equals(node.type()) ? 0xFFE0C060 : 0xFF000000;
            graphics.fill(sx - half - 1, sy - half - 1, sx + half + 2, sy + half + 2, outline);
            graphics.fill(sx - half, sy - half, sx + half + 1, sy + half + 1, 0xFF000000 | RealmMapClient.color(node.owner()));
            if ("stronghold".equals(node.type()) || "capital".equals(node.type())) {
                graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF000000);
            }
            if ("town".equals(node.type()) && node.stat("tier") == 0) {
                graphics.fill(sx - half, sy, sx + half + 1, sy + 1, 0xFF000000); // Ruins: a crack across.
            }
            graphics.drawCenteredString(font, Component.literal(node.name()), sx, sy + half + 3, 0xD0D0D0);
            if (!node.warnings().isEmpty() && blink) {
                graphics.drawString(font, Component.literal("!").withStyle(ChatFormatting.BOLD), sx + half + 2, sy - half - 6, RED);
            }
        }
        if (minecraft.player != null && minecraft.level != null && minecraft.level.dimension().location().toString().equals(shownDim)) {
            int px = screenX(minecraft.player.getX()), py = screenY(minecraft.player.getZ());
            graphics.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
            graphics.drawString(font, Component.literal("You"), px + 5, py - 4, 0xFFFFFF);
        }
        graphics.disableScissor();
        renderLegend(graphics, data, x0, y1 + 3);
        if (inMap(mouseX, mouseY)) {
            RealmMapClient.Node hovered = nodeAt(data, mouseX, mouseY);
            if (hovered != null) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(hovered.name()).withStyle(ChatFormatting.BOLD));
                lines.add(Component.literal(describe(hovered)).withStyle(ChatFormatting.GRAY));
                hovered.warnings().forEach(warning -> lines.add(Component.literal("! " + warning).withStyle(ChatFormatting.RED)));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    private void renderLegend(GuiGraphics graphics, RealmMapClient.Data data, int x, int y) {
        Set<String> owners = new LinkedHashSet<>();
        owners.add("kingdom");
        data.nodes().values().forEach(node -> owners.add(node.owner()));
        for (String owner : owners) {
            graphics.fill(x, y + 1, x + 6, y + 7, 0xFF000000 | RealmMapClient.color(owner));
            graphics.drawString(font, Component.literal(owner), x + 9, y, GREY);
            x += 14 + font.width(owner);
        }
        graphics.drawString(font, Component.literal("! in trouble").withStyle(ChatFormatting.BOLD), x, y, RED);
        x += 6 + font.width("! in trouble");
        graphics.drawString(font, Component.literal("  drag to move, scroll to zoom"), x, y, 0x707070);
    }

    private void renderPanel(GuiGraphics graphics, RealmMapClient.Data data, int mouseX, int mouseY) {
        rows.clear();
        int x0 = width - PANEL_WIDTH - MARGIN, x1 = width - MARGIN;
        graphics.fill(x0 - 1, TOP - 1, x1 + 1, mapBottom() + 1, 0xFF505050);
        graphics.fill(x0, TOP, x1, mapBottom(), 0xF0181818);
        graphics.enableScissor(x0, TOP, x1, mapBottom());
        int x = x0 + 6, w = PANEL_WIDTH - 12;
        int[] y = { TOP + 6 };
        text(graphics, "The Kingdom", x, y, GOLD);
        long next = data.untilNextTurn() / 1000;
        text(graphics, "Turn " + data.turn() + " - next in " + next / 60 + ":" + String.format("%02d", next % 60), x, y, 0xFFFFFF);
        text(graphics, "Army " + data.kingdom().getOrDefault("army", 0) + "   Treasury " + data.kingdom().getOrDefault("treasury", 0), x, y, 0xFFFFFF);
        Collection<RealmMapClient.Node> nodes = data.nodes().values();
        long towns = nodes.stream().filter(node -> "town".equals(node.type())).count();
        long held = nodes.stream().filter(node -> "town".equals(node.type()) && node.kingdoms()).count();
        long strongholds = nodes.stream().filter(node -> "stronghold".equals(node.type()) && node.kingdoms()).count();
        long troubled = nodes.stream().filter(node -> !node.warnings().isEmpty()).count();
        text(graphics, "Towns " + held + "/" + towns + "   Strongholds held " + strongholds, x, y, 0xFFFFFF);
        text(graphics, troubled == 0 ? "Nowhere is in trouble" : troubled + (troubled == 1 ? " place" : " places") + " in trouble", x, y,
                troubled == 0 ? 0x80FF80 : RED);
        y[0] += 4;
        text(graphics, "Nations", x, y, GOLD);
        if (data.nations().isEmpty()) {
            text(graphics, "None in the field", x, y, GREY);
        }
        for (RealmMapClient.Nation nation : data.nations()) {
            String line = nation.id() + ": " + nation.military() + " military, " + nation.strongholds() + " strongholds"
                    + (nation.towns() > 0 ? ", " + nation.towns() + " towns" : "") + (nation.pushedBack() ? " (pushed back)" : "");
            wrapped(graphics, line, x, y, w, RealmMapClient.color(nation.id()));
        }
        if (!data.raids().isEmpty()) {
            y[0] += 4;
            text(graphics, "Raids at dusk", x, y, GOLD);
            for (RealmMapClient.Raid raid : data.raids()) {
                long left = data.until(raid) / 1000;
                String who = "kingdom".equals(raid.nation()) ? "Kingdom's army" : raid.nation();
                row(graphics, raid.to(), who + " -> " + data.name(raid.to()) + " (" + left / 60 + ":" + String.format("%02d", left % 60) + ")", x, y, w,
                        "kingdom".equals(raid.nation()) ? 0x80C0FF : RED, mouseX, mouseY);
            }
        }
        y[0] += 6;
        graphics.fill(x, y[0] - 3, x + w, y[0] - 2, 0xFF404040);
        RealmMapClient.Node node = selected == null ? null : data.nodes().get(selected);
        if (node != null) {
            text(graphics, node.name(), x, y, GOLD);
            wrapped(graphics, describe(node), x, y, w, GREY);
            if (!node.dim().equals(shownDim)) {
                wrapped(graphics, "In " + dimName(node.dim()), x, y, w, GREY);
            }
            node.warnings().forEach(warning -> wrapped(graphics, "! " + warning, x, y, w, RED));
            node.notes().forEach(note -> wrapped(graphics, note, x, y, w, YELLOW));
            StringBuilder stats = new StringBuilder();
            node.stats().forEach((stat, value) -> {
                if (!stat.equals("tier") && !(value == 0 && Set.of("upgrading", "damage", "repair", "wrecked", "rally", "heat").contains(stat))) {
                    stats.append(stats.isEmpty() ? "" : ", ").append(stat).append(" ").append(value);
                }
            });
            wrapped(graphics, stats.toString(), x, y, w, 0xFFFFFF);
            List<String> links = new ArrayList<>();
            for (String[] link : data.links()) {
                if (link[0].equals(node.id()) || link[1].equals(node.id())) {
                    links.add(link[0].equals(node.id()) ? link[1] : link[0]);
                }
            }
            y[0] += 2;
            text(graphics, links.isEmpty() ? "No links" : "Links", x, y, GOLD);
            for (String other : links) {
                RealmMapClient.Node linked = data.nodes().get(other);
                row(graphics, other, "- " + data.name(other) + (linked == null ? "" : " (" + linked.owner() + ")"), x, y, w,
                        linked == null ? GREY : RealmMapClient.color(linked.owner()), mouseX, mouseY);
            }
            y[0] += 2;
            wrapped(graphics, "Click empty map to go back to the trouble list", x, y, w, 0x707070);
        } else {
            text(graphics, "In trouble", x, y, GOLD);
            if (troubled == 0) {
                text(graphics, "All quiet", x, y, GREY);
            }
            for (RealmMapClient.Node trouble : nodes.stream().filter(other -> !other.warnings().isEmpty()).toList()) {
                row(graphics, trouble.id(), "! " + trouble.name() + ": " + trouble.warnings().get(0), x, y, w, RED, mouseX, mouseY);
            }
            y[0] += 4;
            wrapped(graphics, "Click a place on the map or in a list to see it", x, y, w, 0x707070);
        }
        graphics.disableScissor();
    }

    private void text(GuiGraphics graphics, String text, int x, int[] y, int color) {
        graphics.drawString(font, Component.literal(text), x, y[0], color);
        y[0] += LINE;
    }

    private void wrapped(GuiGraphics graphics, String text, int x, int[] y, int width, int color) {
        for (FormattedCharSequence line : font.split(Component.literal(text), width)) {
            graphics.drawString(font, line, x, y[0], color);
            y[0] += LINE;
        }
    }

    /** A panel line that selects a node when clicked, highlighted under the mouse. */
    private void row(GuiGraphics graphics, String id, String text, int x, int[] y, int width, int color, int mouseX, int mouseY) {
        int top = y[0];
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int bottom = top + lines.size() * LINE;
        if (mouseX >= x && mouseX < x + width && mouseY >= top - 1 && mouseY < bottom - 1) {
            graphics.fill(x - 2, top - 1, x + width + 2, bottom - 1, 0x30FFFFFF);
        }
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y[0], color);
            y[0] += LINE;
        }
        rows.add(new Object[] { id, x, top - 1, x + width, bottom - 1 });
    }

    /** "Town, kingdom, tier 2 (village)", "Stronghold, goblins", and so on. */
    private static String describe(RealmMapClient.Node node) {
        String type = Character.toUpperCase(node.type().charAt(0)) + node.type().substring(1);
        String kind = node.kind().isEmpty() ? "" : " (" + node.kind() + ")";
        String tier = "town".equals(node.type()) ? ", tier " + node.stat("tier") + " (" + TIERS[Mth.clamp(node.stat("tier"), 0, TIERS.length - 1)] + ")" : "";
        return type + kind + ", " + node.owner() + tier;
    }

    private static final String[] TIERS = { "ruins", "hamlet", "village", "town", "market town", "city" };

    private static String dimName(String dim) {
        String path = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }

    /** A line of 1-pixel dots: GuiGraphics has no line drawing of its own. */
    private static void line(GuiGraphics graphics, int xa, int ya, int xb, int yb, int color) {
        int steps = Math.max(Math.abs(xb - xa), Math.abs(yb - ya));
        if (steps > 4000) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? xa : xa + (xb - xa) * i / steps;
            int y = steps == 0 ? ya : ya + (yb - ya) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        dragged = false;
        return super.mouseClicked(mouseX, mouseY, button) || button == 0;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        RealmMapClient.Data data = RealmMapClient.latest();
        if (button == 0 && !dragged && data != null) {
            for (Object[] row : rows) {
                if (mouseX >= (int) row[1] && mouseX < (int) row[3] && mouseY >= (int) row[2] && mouseY < (int) row[4]) {
                    select(data, (String) row[0], true);
                    return true;
                }
            }
            if (inMap(mouseX, mouseY)) {
                RealmMapClient.Node node = nodeAt(data, mouseX, mouseY);
                selected = node == null ? null : node.id();
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Selects a node, and if asked, shows its dimension and moves the map to it. */
    private void select(RealmMapClient.Data data, String id, boolean center) {
        RealmMapClient.Node node = data.nodes().get(id);
        if (node == null) {
            return;
        }
        selected = id;
        if (center) {
            if (!node.dim().equals(shownDim)) {
                shownDim = node.dim();
                fit(data);
            }
            centerX = node.x();
            centerZ = node.z();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && inMap(mouseX, mouseY) && scale > 0) {
            dragged = dragged || Math.abs(dragX) + Math.abs(dragY) > 0;
            centerX -= dragX / scale;
            centerZ -= dragY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && scale > 0 && scrollY != 0) {
            // Zoom about the mouse: the block under it stays under it.
            double worldX = centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / scale;
            double worldZ = centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / scale;
            scale = Mth.clamp(scale * (scrollY > 0 ? 1.25 : 0.8), 0.005, 8.0);
            centerX = worldX - (mouseX - (mapLeft() + mapRight()) / 2.0) / scale;
            centerZ = worldZ - (mouseY - (mapTop() + mapBottom()) / 2.0) / scale;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
                    minecraft.setScreen(new RealmScreen());
                }
            }
        }
    }
}
