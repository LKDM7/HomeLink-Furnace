package fr.lkdm.homelink.furnace.client;

import fr.lkdm.homecore.api.client.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Six localized manual pages with keyboard-accessible standard controls. */
public final class FurnaceManualView extends Screen {
    private final Screen parent;
    private int page;
    private HomeLinkScreenLayout layout;
    private static final String[] PAGES = {"start", "energy", "ports", "jobs", "network", "troubleshooting"};
    public FurnaceManualView(Screen parent) { super(Component.translatable("manual.homelink_furnace.title")); this.parent = parent; }
    @Override protected void init() {
        layout = HomeLinkScreenLayout.fit(width, height, 520, 340);
        int buttonWidth = Math.max(28, (layout.width() - 28) / 3);
        addRenderableWidget(HomeLinkButton.builder(Component.translatable("manual.homelink_furnace.previous"), ignored -> page = Math.floorMod(page - 1, PAGES.length))
                .bounds(layout.x() + 10, layout.footerY() + 5, buttonWidth, 18).build());
        addRenderableWidget(HomeLinkButton.builder(Component.translatable("manual.homelink_furnace.back"), ignored -> onClose())
                .bounds(layout.x() + 14 + buttonWidth, layout.footerY() + 5, buttonWidth, 18).build());
        addRenderableWidget(HomeLinkButton.builder(Component.translatable("manual.homelink_furnace.next"), ignored -> page = (page + 1) % PAGES.length)
                .bounds(layout.x() + 18 + buttonWidth * 2, layout.footerY() + 5, buttonWidth, 18).build());
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        HomeLinkUi.frame(graphics, layout.x(), layout.y(), layout.width(), layout.height());
        graphics.drawString(font, Component.translatable("manual.homelink_furnace.title"), layout.x() + 14, layout.y() + 11, HomeLinkTheme.TEXT, false);
        String key = "manual.homelink_furnace." + PAGES[page];
        graphics.drawString(font, Component.translatable(key + ".title"), layout.contentX(), layout.contentY() + 3, HomeLinkTheme.ACCENT, false);
        graphics.drawWordWrap(font, Component.translatable(key + ".body"), layout.contentX(), layout.contentY() + 25, layout.contentWidth(), HomeLinkTheme.TEXT);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
