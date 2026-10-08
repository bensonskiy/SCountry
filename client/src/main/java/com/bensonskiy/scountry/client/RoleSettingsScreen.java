package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.network.CountryDTO;
import com.bensonskiy.scountry.network.SettingsUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Экран редактирования прав одной роли: набор тумблеров (Разрешено/Запрещено)
 * для всех ключей Country.ROLE_PERM_KEYS. Изменения уходят на сервер пакетом
 * SettingsUpdatePacket с action=6, после чего сервер рассылает всем клиентам
 * обновлённый CountrySyncPacket — и экран сам перестраивается в tick().
 */
public class RoleSettingsScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int ROW_H = 20;
    private static final int BTN_H = 16;
    private static final int GAP = 4;

    private final String countryName;
    private final String roleName;
    private final Screen parent;

    private int lastVersion = -1;
    private CountryDTO c;

    private int headerY = 0;
    private int sectionEndY = 100;

    private final Map<Integer, String> labels = new LinkedHashMap<>();

    /** Отображаемое имя для каждого ключа. Порядок = Country.ROLE_PERM_KEYS. */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("invite",    "✉  Приглашать игроков");
        LABELS.put("kick",      "👢  Исключать игроков");
        LABELS.put("claim",     "🗺  Управлять территорией");
        LABELS.put("pvp",       "⚔  PVP");
        LABELS.put("build",     "🏗  Строительство (общий флаг)");
        LABELS.put("break",     "⛏  Ломать блоки");
        LABELS.put("place",     "🧱  Ставить блоки");
        LABELS.put("pickup",    "🎒  Поднятие предметов");
        LABELS.put("interact",  "🔧  Взаимодействие");
        LABELS.put("explosion", "💥  Взрывы");
    }

    public RoleSettingsScreen(String countryName, String roleName, Screen parent) {
        super(Component.literal("Права роли: " + roleName));
        this.countryName = countryName;
        this.roleName = roleName;
        this.parent = parent;
    }

    @Override
    protected void init() {
        c = ClientCountryCache.byName(countryName);
        if (c == null || !c.customRoles.containsKey(roleName)) {
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
        if (c == null || !c.customRoles.containsKey(roleName)) return;

        Map<String, Boolean> perms = c.customRoles.get(roleName);
        boolean canEdit = canEdit();

        int px = (width - PANEL_W) / 2;
        int labelW = 240;
        int btnAreaX = px + labelW + 6;
        int btnAreaW = PANEL_W - labelW - 6;
        int halfW = (btnAreaW - GAP) / 2;

        int y = 50;
        headerY = y;
        y += 14;

        for (String key : LABELS.keySet()) {
            String label = LABELS.get(key);
            labels.put(y, label);

            boolean allowed = perms.getOrDefault(key, true);

            Button allow = Button.builder(
                    Component.literal(allowed ? "§a✓ Разрешено" : "§7Разрешено"),
                    b -> send(key, true)).bounds(btnAreaX, y, halfW, BTN_H).build();
            allow.active = canEdit && !allowed;

            Button deny = Button.builder(
                    Component.literal(!allowed ? "§c✕ Запрещено" : "§7Запрещено"),
                    b -> send(key, false)).bounds(btnAreaX + halfW + GAP, y, halfW, BTN_H).build();
            deny.active = canEdit && allowed;

            addRenderableWidget(allow);
            addRenderableWidget(deny);
            y += ROW_H;
        }

        sectionEndY = y;

        addRenderableWidget(Button.builder(Component.literal("Назад"),
                        b -> Minecraft.getInstance().setScreen(parent))
                .bounds((width - 100) / 2, Math.min(height - 26, y + 8), 100, 18).build());
    }

    private void send(String permKey, boolean value) {
        PacketDistributor.sendToServer(new SettingsUpdatePacket(
                countryName, 6, roleName, permKey + ":" + (value ? "1" : "0")));
    }

    private boolean canEdit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || c == null) return false;
        if (mc.player.hasPermissions(2)) return true;
        String me = mc.player.getGameProfile().getName();
        String role = null;
        for (var e : c.members.entrySet())
            if (e.getKey().equalsIgnoreCase(me)) { role = e.getValue(); break; }
        return "Лидер".equals(role) || "Заместитель".equals(role);
    }

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

        g.drawString(font, "§eРоль: §f" + roleName, px, top, 0xFFFFFF, false);
        g.drawString(font, "§7Страна: §f" + countryName, px, top + 12, 0xFFFFFF, false);
        g.fill(px, top + 26, px + PANEL_W, top + 27, 0xFF2A3038);

        g.drawString(font, "§7⚙ ПРАВА РОЛИ", px, headerY, 0xFFFFFF, false);

        for (var e : labels.entrySet()) {
            g.drawString(font, e.getValue(), px, e.getKey() + 4, 0xFFFFFF, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        int v = ClientCountryCache.version();
        if (v != lastVersion) {
            lastVersion = v;
            rebuild();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}