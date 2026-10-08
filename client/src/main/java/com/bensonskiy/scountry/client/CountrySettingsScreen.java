package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.network.CountryDTO;
import com.bensonskiy.scountry.network.SettingsUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Второе меню: настройки страны, роли, участники.
 * Линейная сетка: каждая строка — LINE_H пикселей.
 * Кнопка ⚙ у каждой роли открывает RoleSettingsScreen.
 */
public class CountrySettingsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int LINE_H = 18;
    private static final int BTN_H = 16;
    private static final int GAP = 4;
    private static final int LABEL_W = 100;
    private static final int ROW_BTN_H = 18;

    private final String countryName;
    private final Screen parent;
    private int lastVersion = -1;

    private CountryDTO c;

    private final Map<Integer, String> labels = new LinkedHashMap<>();

    private EditBox newRoleBox;

    private int sectionSettingsY, sectionRolesY, sectionMembersY, sectionEndY;

    public CountrySettingsScreen(String countryName, Screen parent) {
        super(Component.literal("Настройки страны"));
        this.countryName = countryName;
        this.parent = parent;
    }

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    @Override
    protected void init() {
        c = ClientCountryCache.byName(countryName);
        if (c == null) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        lastVersion = ClientCountryCache.version();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        labels.clear();
        c = ClientCountryCache.byName(countryName);
        if (c == null) return;

        boolean canEdit = canEdit();
        int px = (width - PANEL_W) / 2;
        int pw = PANEL_W;
        int btnAreaX = px + LABEL_W + 6;
        int btnAreaW = pw - LABEL_W - 6;
        int colW = (btnAreaW - 2 * GAP) / 3;

        int y = 44;
        sectionSettingsY = y;
        y += 14;

        String[][] settings = {
                {"⚔ PVP",        "pvp",       c.pvpMode},
                {"⛏ Ломать",     "break",     c.breakMode},
                {"🧱 Ставить",    "place",     c.placeMode},
                {"🎒 Поднятие",   "pickup",    c.pickupMode},
                {"🔧 Взаим.",     "interact",  c.interactMode},
                {"💥 Взрывы",     "explosion", c.explosionMode},
        };
        for (String[] s : settings) {
            labels.put(y, s[0]);
            int bx = btnAreaX;
            addModeBtn(bx,               y, colW, BTN_H, "Все",    s[2], "ALL",           s[1], canEdit);
            addModeBtn(bx + colW + GAP,  y, colW, BTN_H, "Жители", s[2], "ONLY_MEMBERS",  s[1], canEdit);
            addModeBtn(bx + 2 * (colW + GAP), y, colW, BTN_H, "Никто", s[2], "NONE",       s[1], canEdit);
            y += ROW_BTN_H;
        }

        // ---------- Роли ----------
        y += 10;
        sectionRolesY = y;
        y += 14;

        List<String> roles = new ArrayList<>(c.customRoles.keySet());
        if (roles.isEmpty()) {
            labels.put(y, "§8(нет кастомных ролей)");
            y += ROW_BTN_H;
        } else {
            for (String r : roles) {
                labels.put(y, "§7● §f" + r);

                // ⚙ — открыть редактор прав роли.
                addRenderableWidget(Button.builder(Component.literal("§b⚙"),
                                b -> Minecraft.getInstance().setScreen(
                                        new RoleSettingsScreen(countryName, r, this)))
                        .bounds(px + pw - 44, y, 20, BTN_H).build());

                // ✕ — удалить роль.
                if (canEdit) {
                    addRenderableWidget(Button.builder(Component.literal("§c✕"), b -> {
                        PacketDistributor.sendToServer(new SettingsUpdatePacket(countryName, 2, r, ""));
                    }).bounds(px + pw - 20, y, 20, BTN_H).build());
                }
                y += ROW_BTN_H;
            }
        }

        if (canEdit) {
            newRoleBox = new EditBox(font, px, y, pw - 70, BTN_H, Component.literal(""));
            newRoleBox.setMaxLength(20);
            newRoleBox.setHint(Component.literal("§7Новая роль"));
            addRenderableWidget(newRoleBox);
            addRenderableWidget(Button.builder(Component.literal("Создать"), b -> {
                String r = newRoleBox.getValue().trim();
                if (r.isEmpty()) return;
                PacketDistributor.sendToServer(new SettingsUpdatePacket(countryName, 1, r, ""));
                newRoleBox.setValue("");
            }).bounds(px + pw - 66, y, 66, BTN_H).build());
            y += ROW_BTN_H;
        }

        // ---------- Участники ----------
        y += 10;
        sectionMembersY = y;
        y += 14;

        List<Map.Entry<String, String>> members = new ArrayList<>(c.members.entrySet());
        members.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        for (var e : members) {
            String name = e.getKey();
            String role = e.getValue();
            labels.put(y, "§f" + name + " §7(" + role + ")");
            if (canEdit && !name.equalsIgnoreCase(c.leader)) {
                addRenderableWidget(Button.builder(Component.literal("§a👑"),
                                b -> PacketDistributor.sendToServer(
                                        new SettingsUpdatePacket(countryName, 4, name, "")))
                        .bounds(px + pw - 44, y, 20, BTN_H).build());
                addRenderableWidget(Button.builder(Component.literal("§c✕"),
                                b -> PacketDistributor.sendToServer(
                                        new SettingsUpdatePacket(countryName, 3, name, "")))
                        .bounds(px + pw - 20, y, 20, BTN_H).build());
            }
            y += ROW_BTN_H;
        }
        sectionEndY = y;

        addRenderableWidget(Button.builder(Component.literal("Назад"),
                        b -> Minecraft.getInstance().setScreen(parent))
                .bounds((width - 100) / 2, Math.min(height - 26, y + 8), 100, 18).build());
    }

    private void addModeBtn(int x, int y, int w, int h, String text, String current, String value,
                            String key, boolean canEdit) {
        boolean active = value.equals(current);
        String t = (active ? "§a✓ " : "§7") + text;
        Button b = Button.builder(Component.literal(t), btn -> {
            if (!canEdit) return;
            PacketDistributor.sendToServer(new SettingsUpdatePacket(countryName, 0, key, value));
        }).bounds(x, y, w, h).build();
        b.active = canEdit;
        addRenderableWidget(b);
    }

    private boolean canEdit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || c == null) return false;
        if (mc.player.hasPermissions(2)) return true;
        String me = mc.player.getGameProfile().getName();
        String role = null;
        for (var e : c.members.entrySet()) if (e.getKey().equalsIgnoreCase(me)) { role = e.getValue(); break; }
        return "Лидер".equals(role) || "Заместитель".equals(role);
    }

    // ==================== РЕНДЕР ====================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // без ванильного blur
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);

        int px = (width - PANEL_W) / 2;
        int top = 20;
        int bottom = Math.min(height - 6, sectionEndY + 40);
        g.fill(px - 8, top - 6, px + PANEL_W + 8, bottom, 0xF00D1117);
        g.fill(px - 8, top - 6, px + PANEL_W + 8, top - 5, 0xFF2A3038);

        g.drawString(font, "§eСтрана: §f" + c.name, px, top, 0xFFFFFF, false);
        g.drawString(font, "§7Лидер: §f" + c.leader
                        + "   §7Чанков: §f" + c.chunkKeys.size()
                        + "   §7Жителей: §f" + c.members.size(),
                px, top + 12, 0xFFFFFF, false);
        g.fill(px, top + 26, px + PANEL_W, top + 27, 0xFF2A3038);

        g.drawString(font, "§7⚙ НАСТРОЙКИ", px, sectionSettingsY, 0xFFFFFF, false);
        g.drawString(font, "§7🔑 РОЛИ",     px, sectionRolesY,    0xFFFFFF, false);
        g.drawString(font, "§7👥 УЧАСТНИКИ", px, sectionMembersY, 0xFFFFFF, false);

        for (var e : labels.entrySet()) {
            g.drawString(font, e.getValue(), px, e.getKey() + 4, 0xFFFFFF, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ==================== TICK ====================

    @Override
    public void tick() {
        super.tick();
        int v = ClientCountryCache.version();
        if (v != lastVersion) {
            lastVersion = v;
            rebuild();
        }
    }
}