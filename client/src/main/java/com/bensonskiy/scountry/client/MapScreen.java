package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.network.ChunkColorRequestPacket;
import com.bensonskiy.scountry.network.CountryDTO;
import com.bensonskiy.scountry.network.MapActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class MapScreen extends Screen {

    // ===== Камера =====
    private double worldCenterX = 0, worldCenterZ = 0;
    private double scale = 0.35;
    private static final double MIN_SCALE = 0.03, MAX_SCALE = 3.0;
    private static final int CHUNK_BLOCKS = 16;

    // ===== Кисть =====
    private int brush = 1;
    private boolean addMode = true;

    // ===== Панель =====
    private boolean panelOpen = true;
    private static final int PANEL_W = 180;

    private String selected;

    // ===== Панорамирование / рисование =====
    private boolean panning;
    private double panMX, panMY, panCX, panCZ;
    private boolean painting;
    private int lastPaintCX = Integer.MIN_VALUE, lastPaintCZ = Integer.MIN_VALUE;

    private final Map<Long, int[]> pending = new HashMap<>();
    private long lastFlush = 0;
    private static final long FLUSH_INTERVAL_MS = 350;

    private final Set<Long> requestedTiles = new HashSet<>();
    private static final int TILE_CHUNKS = 16;

    private EditBox newNameBox, newLeaderBox;
    private Button brushBtn;

    // Виджеты панели: статика (создаются один раз) + список стран (пересобирается)
    private final List<AbstractWidget> staticWidgets = new ArrayList<>();
    private final List<AbstractWidget> countryWidgets = new ArrayList<>();
    private int lastSeenCountryVersion = -1;

    public MapScreen() { super(Component.translatable("screen.scountry.map")); }

    // ======================= ИНИЦИАЛИЗАЦИЯ =======================

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            worldCenterX = mc.player.getX();
            worldCenterZ = mc.player.getZ();
        }
        buildStaticPanel();
        rebuildCountryList();
        lastSeenCountryVersion = ClientCountryCache.version();
    }

    /** Создаёт статические виджеты панели. Вызывается один раз (или при toggle panelOpen). */
    private void buildStaticPanel() {
        for (var w : staticWidgets) removeWidget(w);
        staticWidgets.clear();

        if (!panelOpen) return;

        int px = width - PANEL_W + 8;
        int py = 32;
        int pw = PANEL_W - 16;
        int rowH = 16;
        int gap = 3;

        // + / − / ⌂
        int bw = (pw - 2 * gap) / 3;
        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"),
                b -> scale = Math.min(MAX_SCALE, scale * 1.25)).bounds(px, py, bw, rowH).build()));
        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("−"),
                b -> scale = Math.max(MIN_SCALE, scale / 1.25)).bounds(px + bw + gap, py, bw, rowH).build()));
        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("⌂"), b -> {
            if (Minecraft.getInstance().player != null) {
                worldCenterX = Minecraft.getInstance().player.getX();
                worldCenterZ = Minecraft.getInstance().player.getZ();
                scale = 0.35;
            }
        }).bounds(px + 2 * (bw + gap), py, bw, rowH).build()));
        py += rowH + gap + 3;

        // Доб. / Убр.
        int halfW = (pw - gap) / 2;
        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("Доб."),
                b -> addMode = true).bounds(px, py, halfW, rowH).build()));
        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("Убр."),
                b -> addMode = false).bounds(px + halfW + gap, py, halfW, rowH).build()));
        py += rowH + gap;

        // Кисть
        brushBtn = addRenderableWidget(Button.builder(brushLabel(), b -> {
            brush = switch (brush) { case 1 -> 3; case 3 -> 5; case 5 -> 7; default -> 1; };
            b.setMessage(brushLabel());
        }).bounds(px, py, pw, rowH).build());
        staticWidgets.add(brushBtn);
        py += rowH + gap + 3;

        // Создать страну
        newNameBox = new EditBox(font, px, py, pw, rowH, Component.literal(""));
        newNameBox.setMaxLength(48);
        newNameBox.setHint(Component.literal("§7Название"));
        staticWidgets.add(addRenderableWidget(newNameBox));
        py += rowH + gap;

        newLeaderBox = new EditBox(font, px, py, pw, rowH, Component.literal(""));
        newLeaderBox.setMaxLength(24);
        newLeaderBox.setHint(Component.literal("§7Лидер"));
        staticWidgets.add(addRenderableWidget(newLeaderBox));
        py += rowH + gap;

        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("Создать"),
                b -> createCountry()).bounds(px, py, pw, rowH).build()));
        py += rowH + gap + 3;

        // Кнопка настроек — работает всегда, открывает экран для selected
        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("⚙ Настройки"),
                b -> {
                    if (selected != null) {
                        Minecraft.getInstance().setScreen(new CountrySettingsScreen(selected, this));
                    }
                }).bounds(px, py, pw, rowH).build()));
        py += rowH + 3;

        staticWidgets.add(addRenderableWidget(Button.builder(Component.literal("↻ Обновить"),
                b -> rebuildCountryList()).bounds(px, py, pw, rowH).build()));

        // Запоминаем, откуда начинать список стран
        staticPanelBottom = py + rowH + 4;
    }

    /** Y-координата, с которой начинается список стран (обновляется при buildStaticPanel). */
    private int staticPanelBottom = 100;

    /** Пересобирает только список кнопок стран. Не трогает поля ввода. */
    private void rebuildCountryList() {
        for (var w : countryWidgets) removeWidget(w);
        countryWidgets.clear();
        if (!panelOpen) return;

        int px = width - PANEL_W + 8;
        int pw = PANEL_W - 16;
        int itemH = 14;
        int listTop = staticPanelBottom;
        int listH = Math.max(20, height - listTop - 24);

        List<CountryDTO> sorted = new ArrayList<>(ClientCountryCache.all());
        sorted.sort(Comparator.comparing(c -> c.name));

        int maxShown = Math.max(1, listH / (itemH + 2));
        int shown = 0;
        for (CountryDTO c : sorted) {
            if (shown >= maxShown) break;
            String name = c.name;
            Button b = Button.builder(
                    Component.literal((selected != null && selected.equals(name) ? "§a● §f" : "§7○ §f") + name),
                    btn -> {
                        selected = Objects.equals(selected, name) ? null : name;
                        rebuildCountryList();
                    }).bounds(px, listTop + shown * (itemH + 2), pw, itemH).build();
            countryWidgets.add(addRenderableWidget(b));
            shown++;
        }
    }

    private Component brushLabel() { return Component.literal("Кисть: " + brush + "×" + brush); }

    // ======================= КООРДИНАТЫ =======================

    public double scale() { return scale; }
    public int chunkPixelSize() { return Math.max(1, (int) Math.round(CHUNK_BLOCKS * scale)); }

    public int chunkToScreenX(int cx) {
        return (int) Math.floor(width / 2.0 + (cx * (double) CHUNK_BLOCKS - worldCenterX) * scale);
    }
    public int chunkToScreenZ(int cz) {
        return (int) Math.floor(height / 2.0 + (cz * (double) CHUNK_BLOCKS - worldCenterZ) * scale);
    }
    public int screenToChunkX(int sx) {
        return (int) Math.floor(((sx - width / 2.0) / scale + worldCenterX) / CHUNK_BLOCKS);
    }
    public int screenToChunkZ(int sz) {
        return (int) Math.floor(((sz - height / 2.0) / scale + worldCenterZ) / CHUNK_BLOCKS);
    }
    public double worldToScreenX(int wx) { return width / 2.0 + (wx - worldCenterX) * scale; }
    public double worldToScreenZ(int wz) { return height / 2.0 + (wz - worldCenterZ) * scale; }

    public boolean isSelected(String name) { return Objects.equals(selected, name); }
    public CountryDTO getCountryAt(int cx, int cz) { return ClientCountryCache.byChunk(cx, cz); }

    public double rawMouseX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * width / mc.getWindow().getWidth();
    }
    public double rawMouseY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * height / mc.getWindow().getHeight();
    }

    // ======================= РЕНДЕР =======================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // намеренно пусто — убирает ванильный blur
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapRenderer.draw(g, this, 0, 0, width, height);

        if (panelOpen) {
            int px = width - PANEL_W;
            g.fill(px, 0, width, height, 0xCC0D1117);
            g.fill(px, 0, px + 1, height, 0xFF2A3038);
        }

        g.drawString(font, "§7SCountry §8| §7" + (addMode ? "§aADD" : "§cREMOVE")
                + " §7| кисть §f" + brush + "×" + brush, 6, 6, 0xFFFFFF, false);

        int cx = screenToChunkX(mouseX), cz = screenToChunkZ(mouseY);
        CountryDTO hover = ClientCountryCache.byChunk(cx, cz);
        String info = "§7чанк §f" + cx + ", " + cz;
        if (hover != null) info += " §8| §e" + hover.name + " §7(" + hover.leader + ")";
        g.drawString(font, info, 6, 18, 0xFFFFFF, false);

        int btnX = width - (panelOpen ? PANEL_W + 20 : 20);
        g.fill(btnX, 4, btnX + 16, 20, 0xCC161B22);
        g.fill(btnX, 4, btnX + 16, 5, 0xFF2A3038);
        g.fill(btnX, 19, btnX + 16, 20, 0xFF2A3038);
        g.drawString(font, panelOpen ? "▶" : "◀", btnX + 4, 8, 0xFFFFFF, false);

        g.drawString(font, "§8ЛКМ рисовать · ПКМ тащить · колесо зум · ] закрыть",
                6, height - 12, 0xFFFFFF, false);

        if (selected != null && panelOpen) {
            // индикатор выбранной страны над кнопкой настроек — опционально
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ======================= ВВОД =======================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int btnX = width - (panelOpen ? PANEL_W + 20 : 20);
        if (mx >= btnX && mx <= btnX + 16 && my >= 4 && my <= 20) {
            panelOpen = !panelOpen;
            buildStaticPanel();
            rebuildCountryList();
            return true;
        }

        if (super.mouseClicked(mx, my, button)) return true;
        if (panelOpen && mx >= width - PANEL_W) return false;

        if (button == 0 && selected != null && hasAdmin()) {
            painting = true;
            lastPaintCX = Integer.MIN_VALUE;
            paintAt(mx, my);
            return true;
        }
        if (button == 1) {
            panning = true;
            panMX = mx; panMY = my;
            panCX = worldCenterX; panCZ = worldCenterZ;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            worldCenterX = panCX - (mx - panMX) / scale;
            worldCenterZ = panCZ - (my - panMY) / scale;
            return true;
        }
        if (painting && button == 0) { paintAt(mx, my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning)  { panning = false; return true; }
        if (painting) { painting = false; flush(); return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (panelOpen && mx >= width - PANEL_W) return false;
        double oldScale = scale;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * (scrollY > 0 ? 1.15 : 1 / 1.15)));
        double wx = (mx - width / 2.0) / oldScale + worldCenterX;
        double wz = (my - height / 2.0) / oldScale + worldCenterZ;
        worldCenterX = wx - (mx - width / 2.0) / scale;
        worldCenterZ = wz - (my - height / 2.0) / scale;
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ======================= ОТПРАВКА ДЕЙСТВИЙ =======================

    private void paintAt(double mx, double my) {
        int cx = screenToChunkX((int) mx), cz = screenToChunkZ((int) my);
        if (cx == lastPaintCX && cz == lastPaintCZ) return;
        lastPaintCX = cx; lastPaintCZ = cz;

        int r = brush / 2;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int x = cx + dx, z = cz + dz;
                pending.put(((long) x << 32) | (z & 0xFFFFFFFFL), new int[]{x, z});
            }
        if (System.currentTimeMillis() - lastFlush > FLUSH_INTERVAL_MS) flush();
    }

    private void flush() {
        lastFlush = System.currentTimeMillis();
        if (pending.isEmpty() || selected == null) return;
        List<int[]> chunks = new ArrayList<>(pending.values());
        pending.clear();
        PacketDistributor.sendToServer(new MapActionPacket(selected, addMode, chunks));
    }

    private void createCountry() {
        String name = newNameBox.getValue().trim();
        String leader = newLeaderBox.getValue().trim();
        if (name.isEmpty() || leader.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand("c new " + name + " " + leader);
        newNameBox.setValue("");
        newLeaderBox.setValue("");
    }

    private boolean hasAdmin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2);
    }

    // ======================= TICK =======================

    @Override
    public void tick() {
        super.tick();
        int mapW = panelOpen ? width - PANEL_W : width;

        int cx0 = screenToChunkX(0)      - 1;
        int cz0 = screenToChunkZ(0)      - 1;
        int cx1 = screenToChunkX(mapW)   + 1;
        int cz1 = screenToChunkZ(height) + 1;

        int tx0 = Math.floorDiv(cx0, TILE_CHUNKS), tx1 = Math.floorDiv(cx1, TILE_CHUNKS);
        int tz0 = Math.floorDiv(cz0, TILE_CHUNKS), tz1 = Math.floorDiv(cz1, TILE_CHUNKS);

        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                long key = ((long) tx << 32) | (tz & 0xFFFFFFFFL);
                if (requestedTiles.contains(key)) continue;
                if (ClientChunkColorCache.hasTile(tx * TILE_CHUNKS, tz * TILE_CHUNKS, TILE_CHUNKS, TILE_CHUNKS))
                    continue;
                requestedTiles.add(key);
                PacketDistributor.sendToServer(new ChunkColorRequestPacket(
                        tx * TILE_CHUNKS, tz * TILE_CHUNKS, TILE_CHUNKS, TILE_CHUNKS));
            }
        }

        // Пересобираем список стран ТОЛЬКО когда данные реально изменились.
        // Это не трогает EditBox'ы (они в staticWidgets).
        int v = ClientCountryCache.version();
        if (v != lastSeenCountryVersion) {
            lastSeenCountryVersion = v;
            if (selected != null && ClientCountryCache.byName(selected) == null) selected = null;
            rebuildCountryList();
        }
    }
}