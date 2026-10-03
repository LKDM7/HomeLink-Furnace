package fr.lkdm.homelink.furnace.client;

import fr.lkdm.homecore.api.client.ui.*;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homelink.furnace.menu.FurnaceMenu;
import fr.lkdm.homelink.furnace.network.FurnacePayloads;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Official HomeCore chrome, with one coherent scaled hit-test space on small viewports. */
public final class FurnaceScreen extends AbstractContainerScreen<FurnaceMenu> {
    private int tab, networkChoice = -1, physicalWidth, physicalHeight;
    private float uiScale = 1;
    private EditBox name;
    public FurnaceScreen(FurnaceMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override protected void init() {
        physicalWidth = width; physicalHeight = height;
        uiScale = Math.min(1, Math.min(width / 380f, height / 328f));
        width = Math.max(380, (int)(width / uiScale)); height = Math.max(328, (int)(height / uiScale));
        var layout = HomeLinkScreenLayout.fit(width, height, 520, 340);
        imageWidth = layout.width(); imageHeight = layout.height();
        super.init();
        leftPos = layout.x(); topPos = layout.y(); menu.layoutSlots(imageWidth, imageHeight);
        inventoryLabelY = imageHeight - 107;
        rebuild();
    }
    private static Component text(String suffix) { return Component.translatable("gui.homelink_furnace." + suffix); }
    private void button(String key, int x, int y, int width, Runnable action) {
        addRenderableWidget(HomeLinkButton.builder(text(key), ignored -> action.run()).bounds(leftPos + x, topPos + y, width, 18)
                .tooltip(Tooltip.create(text("tooltip." + key))).build());
    }
    private void rebuild() {
        clearWidgets();
        String[] tabs = {"production", "energy", "settings"};
        int tabWidth = (imageWidth - 62) / 3;
        for (int i = 0; i < 3; i++) {
            int selected = i;
            addRenderableWidget(HomeLinkButton.builder(text(tabs[i]), ignored -> {
                tab = selected; menu.setClientTab(tab); send(FurnaceMenu.TAB_PRODUCTION + tab); rebuild();
            }).bounds(leftPos + 12 + i * tabWidth, topPos + 36, tabWidth - 3, 18).build().navigation(tab == i));
        }
        button("help", imageWidth - 38, 36, 26, () -> minecraft.setScreen(new FurnaceManualView(this)));
        if (tab == 0) button("collect_xp", 12, imageHeight - 27, 144, () -> send(FurnaceMenu.XP));
        if (tab == 2) {
            name = addRenderableWidget(HomeLinkUi.input(new EditBox(font, leftPos + 16, topPos + 96, imageWidth - 128, 18, text("name"))));
            name.setMaxLength(Renamable.MAX_LENGTH);
            button("rename", imageWidth - 100, 96, 84, () -> PacketDistributor.sendToServer(new FurnacePayloads.Rename(menu.position(), name.getValue())));
            button("power", 16, 132, 144, () -> send(FurnaceMenu.POWER));
            button("redstone", 16, 176, imageWidth - 32, () -> send(FurnaceMenu.REDSTONE));
            button("network_select", 16, 220, imageWidth - 128, () -> {
                var choices = FurnacePayloads.ClientChoices.get(menu.position());
                networkChoice++; if (networkChoice >= choices.size()) networkChoice = -1;
            });
            button("network_apply", imageWidth - 100, 220, 84, () -> {
                var choices = FurnacePayloads.ClientChoices.get(menu.position());
                Optional<UUID> selected = networkChoice >= 0 && networkChoice < choices.size() ? Optional.of(choices.get(networkChoice).id()) : Optional.empty();
                PacketDistributor.sendToServer(new FurnacePayloads.Bind(menu.position(), selected));
            });
        }
    }
    private void send(int action) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose(); graphics.pose().scale(uiScale, uiScale, 1);
        super.render(graphics, (int)(mouseX / uiScale), (int)(mouseY / uiScale), partialTick);
        renderTooltip(graphics, (int)(mouseX / uiScale), (int)(mouseY / uiScale));
        int logicalX = (int)(mouseX / uiScale) - leftPos, logicalY = (int)(mouseY / uiScale) - topPos;
        if (tab == 0 && logicalX >= 12 && logicalX <= imageWidth - 12 && logicalY >= 68 && logicalY <= 82)
            graphics.renderTooltip(font, text("tooltip.status"), (int)(mouseX / uiScale), (int)(mouseY / uiScale));
        if (tab == 1 && logicalX >= 12 && logicalX <= imageWidth - 12 && logicalY >= 72 && logicalY <= 106)
            graphics.renderTooltip(font, text("tooltip.energy"), (int)(mouseX / uiScale), (int)(mouseY / uiScale));
        if (tab == 2 && logicalX >= 12 && logicalX <= imageWidth - 12 && logicalY >= 270 && logicalY <= 307)
            graphics.renderTooltip(font, Component.translatable("gui.homelink_furnace.ports."+menu.tier().number()), (int)(mouseX / uiScale), (int)(mouseY / uiScale));
        graphics.pose().popPose();
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        HomeLinkUi.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        HomeLinkUi.mark(graphics, leftPos + 14, topPos + 8, HomeLinkTheme.ACCENT);
        graphics.drawString(font, HomeLinkUi.clip(font, title.getString(), imageWidth - 65), leftPos + 35, topPos + 11, HomeLinkTheme.TEXT, false);
        HomeLinkUi.panel(graphics, leftPos + 10, topPos + 61, imageWidth - 20, imageHeight - 94);
        if (tab == 0) production(graphics); else if (tab == 1) energy(graphics); else settings(graphics);
    }
    private void line(GuiGraphics graphics, String key, Object value, int x, int y, int available) {
        String resolved = text(key).getString() + ": " + value;
        graphics.drawString(font, HomeLinkUi.clip(font, resolved, available), leftPos + x, topPos + y, HomeLinkTheme.TEXT, false);
    }
    private void label(GuiGraphics graphics, String key, int x, int y) { graphics.drawString(font, text(key), leftPos + x, topPos + y, HomeLinkTheme.MUTED, false); }
    private void production(GuiGraphics graphics) {
        var status = menu.status();
        HomeLinkStatusTone tone = switch(status.deviceState()) {
            case WARNING -> HomeLinkStatusTone.WARNING;
            case ONLINE -> HomeLinkStatusTone.ONLINE;
            default -> HomeLinkStatusTone.NEUTRAL;
        };
        HomeLinkUi.statusDot(graphics, leftPos + 16, topPos + 71, tone);
        graphics.drawString(font, Component.translatable(status.translationKey()), leftPos + 30, topPos + 71, HomeLinkTheme.statusColor(tone), false);
        line(graphics, "temperature", menu.value(3) / 100 + "%", imageWidth - 170, 71, 155);
        line(graphics, "jobs", menu.value(7) + "/" + Math.max(1,menu.value(0)), 16, 88, imageWidth / 2 - 20);
        line(graphics, "queue", menu.value(9), imageWidth / 2, 88, imageWidth / 2 - 16);
        int laneWidth = (imageWidth - 50) / 4;
        for (int lane = 0; lane < Math.max(1,Math.min(menu.tier().jobs(),menu.value(0))); lane++) {
            int x = leftPos + 16 + lane % 4 * (laneWidth + 6), y = topPos + 105 + lane / 4 * 16;
            graphics.drawString(font, Integer.toString(lane + 1), x, y, HomeLinkTheme.MUTED, false);
            HomeLinkUi.progressBar(graphics, x + 11, y + 2, laneWidth - 14, 5, menu.value(19 + lane) / 10000.0, HomeLinkTheme.ACCENT);
        }
        label(graphics, "input", 14, 132); label(graphics, "output", imageWidth - 176, 132);
        for (var slot : menu.slots) if (slot.isActive()) HomeLinkUi.slot(graphics, leftPos + slot.x, topPos + slot.y);
        line(graphics, "processed", menu.wide(17), 14, 198, imageWidth / 2 - 20);
        line(graphics, "xp", String.format(Locale.ROOT, "%.2f", menu.wide(15) / 1000.0), imageWidth / 2, 198, imageWidth / 2 - 16);
        line(graphics, "pending", menu.value(8), imageWidth - 152, imageHeight - 22, 140);
    }
    private void energy(GuiGraphics graphics) {
        int panelWidth = imageWidth - 32;
        line(graphics, "energy_stored", menu.wide(10) + " / " + menu.wide(12) + " HE", 16, 76, panelWidth);
        HomeLinkUi.progressBar(graphics, leftPos + 16, topPos + 96, panelWidth, 8, menu.wide(10) / (double)Math.max(1, menu.wide(12)), HomeLinkTheme.ACCENT);
        line(graphics, "energy_draw", menu.value(14)+" HE/t", 16, 123, panelWidth);
        line(graphics, "operation_cost", menu.value(28) + " HE / 200 t", 16, 148, panelWidth);
        line(graphics, "network", text(menu.value(6) != 0 ? "connected" : "unbound").getString(), 16, 173, panelWidth);
        String[] states = {"cold", "warming", "ready", "processing", "cooling"};
        line(graphics, "heat", Component.translatable("heat.homelink_furnace." + states[Math.max(0, Math.min(4, menu.value(2)))]).getString(), 16, 198, panelWidth);
        line(graphics, "idle_cost", menu.value(27) + " HE/min", 16, 223, panelWidth);
        graphics.drawWordWrap(font, text("energy_note"), leftPos + 16, topPos + 255, panelWidth, HomeLinkTheme.MUTED);
    }
    private void settings(GuiGraphics graphics) {
        label(graphics, "name", 16, 78);
        line(graphics, "power_state", text(menu.value(4) != 0 ? "on" : "off").getString(), 172, 137, imageWidth - 188);
        String[] modes = {"ignore", "require_signal", "require_no_signal"};
        line(graphics, "redstone_mode", Component.translatable("redstone.homelink_furnace." + modes[Math.max(0, Math.min(2, menu.value(5)))]).getString(), 16, 160, imageWidth - 32);
        var choices = FurnacePayloads.ClientChoices.get(menu.position());
        line(graphics, "network", networkChoice >= 0 && networkChoice < choices.size() ? choices.get(networkChoice).name() : text("unbound").getString(), 16, 204, imageWidth - 32);
        line(graphics, "tier", menu.tier().name() + " · " + menu.tier().width() + "×" + menu.tier().height() + "×" + menu.tier().depth(), 16, 252, imageWidth - 32);
        portDiagram(graphics);
    }
    private void portDiagram(GuiGraphics graphics) {
        int x = leftPos + 16, y = topPos + 273, cellSize = 13;
        var tier = menu.tier();
        for (int column = 0; column < tier.width(); column++) for (int layer = 0; layer < tier.height(); layer++) {
            int cellX = x + column * 16, cellY = y + (tier.height()-1-layer)*16;
            HomeLinkUi.panel(graphics, cellX, cellY, cellSize, cellSize);
            graphics.renderOutline(cellX,cellY,cellSize,cellSize,HomeLinkTheme.LINE);
        }
        int inputY = y - 2;
        graphics.fill(x + 4,inputY,x + 9,inputY + 3,HomeLinkTheme.ONLINE);
        int lastX=x+(tier.width()-1)*16, bottomY=y+(tier.height()-1)*16;
        graphics.fill(lastX + 4,bottomY + 10,lastX + 9,bottomY + 13,HomeLinkTheme.WARNING);
        graphics.fill(lastX + 11,bottomY + 4,lastX + 14,bottomY + 9,HomeLinkTheme.ACCENT);
        int labelX=x+44;
        graphics.drawString(font,text("diagram.input"),labelX,y,HomeLinkTheme.ONLINE,false);
        graphics.drawString(font,text("diagram.output"),labelX,y+11,HomeLinkTheme.WARNING,false);
        graphics.drawString(font,text("diagram.energy"),labelX,y+22,HomeLinkTheme.ACCENT,false);
        graphics.drawString(font,HomeLinkUi.clip(font,text("diagram.note").getString(),imageWidth-188),leftPos+172,y+3,HomeLinkTheme.MUTED,false);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
    @Override public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked(x / uiScale, y / uiScale, button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(x / uiScale, y / uiScale, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return super.mouseScrolled(x / uiScale, y / uiScale, horizontal, vertical); }
    @Override public boolean isPauseScreen() { return false; }
    public float uiScale() { return uiScale; }
    public double slotMouseX(int slot) { return (leftPos + menu.slots.get(slot).x + 8) * uiScale; }
    public double slotMouseY(int slot) { return (topPos + menu.slots.get(slot).y + 8) * uiScale; }
}
