package com.bensonskiy.scountry.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Мини-модалка для показа короткого сообщения.
 * Закрывается по кнопке OK, по Esc и по клику вне панели.
 * parent==null → просто закрывается.
 */
public class InfoScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 80;

    private final Component message;
    private final Screen parent;

    public InfoScreen(Component message, Screen parent) {
        super(Component.literal("SCountry"));
        this.message = message;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("OK"), b -> onClose())
                .bounds(cx - 50, cy + PANEL_H / 2 - 24, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Отключаем ванильный blur.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Затемнение.
        g.fill(0, 0, width, height, 0x80000000);

        int cx = width / 2;
        int cy = height / 2;
        int left = cx - PANEL_W / 2;
        int top = cy - PANEL_H / 2;
        int right = cx + PANEL_W / 2;
        int bottom = cy + PANEL_H / 2;

        // Панель.
        g.fill(left, top, right, bottom, 0xF00D1117);
        g.fill(left, top, right, top + 1, 0xFF2A3038);
        g.fill(left, bottom - 1, right, bottom, 0xFF2A3038);

        // Сообщение по центру, с переносом, если очень длинное.
        var lines = font.split(message, PANEL_W - 20);
        int textY = cy - (lines.size() * font.lineHeight) / 2 - 4;
        for (var line : lines) {
            g.drawCenteredString(font, line, cx, textY, 0xFFFFFF);
            textY += font.lineHeight;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Клик вне панели — закрыть.
        int cx = width / 2;
        int cy = height / 2;
        int left = cx - PANEL_W / 2;
        int top = cy - PANEL_H / 2;
        int right = cx + PANEL_W / 2;
        int bottom = cy + PANEL_H / 2;
        if (mx < left || mx > right || my < top || my > bottom) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}