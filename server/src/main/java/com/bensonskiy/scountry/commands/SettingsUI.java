package com.bensonskiy.scountry.commands;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.*;

/**
 * Строит и отправляет кликабельный UI настроек страны.
 * Основное меню — в чате; списки взаимодействий (обычные и Create) открываются
 * в виде книги, разбитой по страницам-категориям, чтобы не засорять чат.
 * Все методы статические — доступ через SCountryServer.countryManager.
 */
public class SettingsUI {

    /** Является ли ключ взаимодействием Create. */
    private static boolean isCreateKey(String key) { return key.startsWith("create:"); }

    // ==================== ГЛАВНОЕ МЕНЮ ====================

    public static void showSettings(ServerPlayer player, String countryName) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null) { player.sendSystemMessage(err("Страна не найдена!")); return; }

        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6▌        ⚙ НАСТРОЙКИ СТРАНЫ        "));
        player.sendSystemMessage(Component.literal("§6▌  §e" + country.name));
        player.sendSystemMessage(Component.literal(""));

        if (canEdit) {
            sendSettingLine(player, country, "⚔ PVP",           "pvp",       country.pvpMode,       country.pvpLocked);
            sendSettingLine(player, country, "⛏ Ломать блоки",  "break",     country.getBreakMode(), country.breakLocked);
            sendSettingLine(player, country, "🧱 Ставить блоки", "place",     country.getPlaceMode(), country.placeLocked);
            sendSettingLine(player, country, "🎒 Поднятие",      "pickup",    country.pickupMode,    country.pickupLocked);
            sendInteractHeader(player, country, true);
            sendCreateHeader(player, country, true);
            sendSettingLine(player, country, "💥 Взрывы",        "explosion", country.explosionMode, country.explosionLocked);
        } else {
            player.sendSystemMessage(Component.literal("§e⚔ PVP: §f"           + modeDisplay(country.pvpMode)));
            player.sendSystemMessage(Component.literal("§e⛏ Ломать блоки: §f"  + modeDisplay(country.getBreakMode())));
            player.sendSystemMessage(Component.literal("§e🧱 Ставить блоки: §f" + modeDisplay(country.getPlaceMode())));
            player.sendSystemMessage(Component.literal("§e🎒 Поднятие: §f"      + modeDisplay(country.pickupMode)));
            sendInteractHeader(player, country, false);
            sendCreateHeader(player, country, false);
            player.sendSystemMessage(Component.literal("§e💥 Взрывы: §f"        + modeDisplay(country.explosionMode)));
        }

        if (player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§6🔒 Блокировка настроек:"));
            sendLockLine(player, country, "pvp",       country.pvpLocked);
            sendLockLine(player, country, "break",     country.breakLocked);
            sendLockLine(player, country, "place",     country.placeLocked);
            sendLockLine(player, country, "pickup",    country.pickupLocked);
            sendLockLine(player, country, "interact",  country.interactLocked);
            sendLockLine(player, country, "create",    country.createLocked);
            sendLockLine(player, country, "explosion", country.explosionLocked);
        }
        player.sendSystemMessage(Component.literal(""));
    }

    // ==================== КНИГА ВЗАИМОДЕЙСТВИЙ ====================

    /**
     * Открывает книгу настроек взаимодействий.
     * @param type      "interact" — обычные взаимодействия; "create" — блоки Create.
     * @param focusCat  категория, которую показать первой (после изменения настройки),
     *                  либо null — обычный порядок.
     */
    public static void openBook(ServerPlayer player, String countryName, String type, String focusCat) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null) { player.sendSystemMessage(err("Страна не найдена!")); return; }

        boolean create = "create".equals(type);
        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);

        List<MutableComponent> pages = buildPages(country, create, canEdit, focusCat, null);
        if (pages.isEmpty()) {
            player.sendSystemMessage(Component.literal(create
                    ? "§7Взаимодействия Create ещё не зарегистрированы."
                    : "§7Взаимодействия ещё не зарегистрированы."));
            return;
        }
        openBookItem(player, buildBook(pages, create));
    }

    /** Открывает книгу взаимодействий РОЛИ (кнопки Разрешено/Запрещено вместо режимов страны). */
    public static void openRoleBook(ServerPlayer player, String countryName, String roleName, String type, String focusCat) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null || !country.hasCustomRole(roleName)) {
            player.sendSystemMessage(err("Роль не найдена!"));
            return;
        }

        boolean create = "create".equals(type);
        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);

        List<MutableComponent> pages = buildPages(country, create, canEdit, focusCat, roleName);
        if (pages.isEmpty()) {
            player.sendSystemMessage(Component.literal(create
                    ? "§7Взаимодействия Create ещё не зарегистрированы."
                    : "§7Взаимодействия ещё не зарегистрированы."));
            return;
        }
        openBookItem(player, buildBook(pages, create));
    }

    /** Изменение права РОЛИ из книги: применяет и переоткрывает книгу на той же категории. */
    public static void changeRoleSettingBook(ServerPlayer player, String countryName, String roleName,
                                             String type, String cat, String key, boolean value) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null || !country.hasCustomRole(roleName)) {
            player.sendSystemMessage(err("Роль не найдена!"));
            return;
        }

        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);
        if (!canEdit) { player.sendSystemMessage(err("Нет прав!")); return; }
        if (!CountryManager.modRegistry.containsKey(key)) {
            player.sendSystemMessage(err("Неизвестное взаимодействие: " + key));
            return;
        }

        country.setCustomRolePermission(roleName, key, value);
        mgr.saveAll();
        openRoleBook(player, countryName, roleName, type, cat);
    }

    /** Изменение настройки из книги: применяет и переоткрывает книгу на той же категории. */
    public static void changeSettingBook(ServerPlayer player, String countryName,
                                         String type, String cat, String key, String value) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null) { player.sendSystemMessage(err("Страна не найдена!")); return; }

        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);
        if (!canEdit) { player.sendSystemMessage(err("Нет прав!")); return; }
        if (!value.equals("ALL") && !value.equals("ONLY_MEMBERS") && !value.equals("NONE")) return;

        boolean create = "create".equals(type);
        boolean locked = create ? country.createLocked : country.interactLocked;
        if (!player.hasPermissions(2) && locked) {
            player.sendSystemMessage(err(create
                    ? "Настройка Create заблокирована администратором!"
                    : "Настройка взаимодействий заблокирована администратором!"));
            return;
        }
        if (!CountryManager.modRegistry.containsKey(key)) {
            player.sendSystemMessage(err("Неизвестное взаимодействие: " + key));
            return;
        }

        country.modSettings.put(key, value);
        mgr.saveAll();
        openBook(player, countryName, type, cat);
    }

    /** Ставит режим сразу всем пунктам категории (кнопка «Вся категория» в книге). */
    public static void changeCategoryBook(ServerPlayer player, String countryName,
                                          String type, String cat, String value) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null) { player.sendSystemMessage(err("Страна не найдена!")); return; }

        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);
        if (!canEdit) { player.sendSystemMessage(err("Нет прав!")); return; }
        if (!value.equals("ALL") && !value.equals("ONLY_MEMBERS") && !value.equals("NONE")) return;

        boolean create = "create".equals(type);
        boolean locked = create ? country.createLocked : country.interactLocked;
        if (!player.hasPermissions(2) && locked) {
            player.sendSystemMessage(err(create
                    ? "Настройка Create заблокирована администратором!"
                    : "Настройка взаимодействий заблокирована администратором!"));
            return;
        }

        int changed = 0;
        for (String key : keysOfCategory(cat, create)) {
            country.modSettings.put(key, value);
            changed++;
        }
        if (changed == 0) { player.sendSystemMessage(err("Категория пуста: " + cat)); return; }
        mgr.saveAll();
        openBook(player, countryName, type, cat);
    }

    /** То же для роли: разрешить или запретить всю категорию разом. */
    public static void changeRoleCategoryBook(ServerPlayer player, String countryName, String roleName,
                                              String type, String cat, boolean value) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null || !country.hasCustomRole(roleName)) {
            player.sendSystemMessage(err("Роль не найдена!"));
            return;
        }
        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);
        if (!canEdit) { player.sendSystemMessage(err("Нет прав!")); return; }

        boolean create = "create".equals(type);
        int changed = 0;
        for (String key : keysOfCategory(cat, create)) {
            country.setCustomRolePermission(roleName, key, value);
            changed++;
        }
        if (changed == 0) { player.sendSystemMessage(err("Категория пуста: " + cat)); return; }
        mgr.saveAll();
        openRoleBook(player, countryName, roleName, type, cat);
    }

    /** Ключи взаимодействий, попадающие в категорию (в порядке реестра). */
    private static List<String> keysOfCategory(String catId, boolean create) {
        List<String> out = new ArrayList<>();
        for (String key : CountryManager.modRegistry.keySet()) {
            if (create != isCreateKey(key)) continue;
            if (catOf(key).equals(catId)) out.add(key);
        }
        return out;
    }

    // ---- Категории ----

    private static final LinkedHashMap<String, String> INTERACT_CATS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> CREATE_CATS   = new LinkedHashMap<>();
    private static final Map<String, String> KEY_CAT = new HashMap<>();

    static {
        INTERACT_CATS.put("blocks",     "§6🧱 Блоки мира");
        INTERACT_CATS.put("food",       "§6🍎 Еда и зелья");
        INTERACT_CATS.put("projectile", "§6🏹 Снаряды");
        INTERACT_CATS.put("tools",      "§6🧰 Инструменты");
        INTERACT_CATS.put("misc",       "§6🧩 Прочее");

        CREATE_CATS.put("kinetics",  "§6⚙ Механизмы");
        CREATE_CATS.put("redstone",  "§6🔴 Логика");
        CREATE_CATS.put("logistics", "§6📦 Логистика");
        CREATE_CATS.put("info",      "§6🖥 Информация");
        CREATE_CATS.put("misc",      "§6🧩 Прочее");

        cat("blocks",     "vanilla:containers", "vanilla:doors", "vanilla:redstone",
                "vanilla:workstations", "vanilla:beds");
        cat("food",       "eat", "drink", "xp_bottle", "potion");
        cat("projectile", "ranged", "trident", "throw", "firework");
        cat("tools",      "bucket", "fire", "bonemeal", "fishing", "shears",
                "lead", "nametag", "enderpearl", "endereye");

        cat("kinetics",  "create:wrench", "create:rotation_speed_controller", "create:creative_motor",
                "create:adjustable_chain_gearshift", "create:sequenced_gearshift",
                "create:weighted_ejector", "create:deployer", "create:mechanical_arm",
                "create:mechanical_crafter", "create:schematicannon", "create:elevator_contact");
        cat("redstone",  "create:analog_lever", "create:redstone_contact", "create:redstone_link",
                "create:content_observer", "create:stockpile_switch",
                "create:latches", "create:timers");
        cat("logistics", "create:smart_chute", "create:smart_fluid_pipe", "create:brass_tunnel",
                "create:item_hatch", "create:funnels", "create:interfaces");
        cat("info",      "create:gauges", "create:displays");
        cat("misc",      "create:super_glue");
    }

    private static void cat(String id, String... keys) { for (String k : keys) KEY_CAT.put(k, id); }
    private static String catOf(String key) { return KEY_CAT.getOrDefault(key, "misc"); }

    // ---- Построение страниц книги ----

    private static final int ENTRIES_PER_PAGE = 4;

    private static List<MutableComponent> buildPages(Country country, boolean create,
                                                     boolean canEdit, String focusCat, String roleName) {
        LinkedHashMap<String, String> catTitles = create ? CREATE_CATS : INTERACT_CATS;

        LinkedHashMap<String, List<String>> byCat = new LinkedHashMap<>();
        for (String id : catTitles.keySet()) byCat.put(id, new ArrayList<>());
        for (String key : CountryManager.modRegistry.keySet()) {
            if (create != isCreateKey(key)) continue;
            byCat.get(catOf(key)).add(key);
        }

        List<String> order = new ArrayList<>(catTitles.keySet());
        if (focusCat != null && order.remove(focusCat)) order.add(0, focusCat);

        List<MutableComponent> pages = new ArrayList<>();
        for (String catId : order) {
            List<String> keys = byCat.get(catId);
            if (keys == null || keys.isEmpty()) continue;
            for (int i = 0; i < keys.size(); i += ENTRIES_PER_PAGE) {
                MutableComponent page = Component.literal(
                        catTitles.get(catId) + (i > 0 ? " §7(прод.)" : "") + "\n");
                appendCategorySwitch(page, country, catId, keys, create, canEdit, roleName);
                page.append(Component.literal("§8──────────\n"));
                for (int j = i; j < Math.min(i + ENTRIES_PER_PAGE, keys.size()); j++) {
                    appendEntry(page, country, keys.get(j), create, catId, canEdit, roleName);
                }
                pages.add(page);
            }
        }
        return pages;
    }

    private static void appendCategorySwitch(MutableComponent page, Country country, String catId,
                                             List<String> keys, boolean create, boolean canEdit,
                                             String roleName) {
        page.append(Component.literal("§8Вся категория:\n"));

        if (roleName != null) {
            Boolean common = commonRolePermission(country, roleName, keys);
            String label = common == null ? "[Разное]" : (common ? "[Разрешено]" : "[Запрещено]");
            ChatFormatting color = common == null ? ChatFormatting.DARK_GRAY
                    : (common ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED);
            boolean next = common == null ? false : !common;
            if (canEdit) {
                String cmd = "/c rank " + CountryCommand.q(country.name) + " rolebookcat " + roleName + " "
                        + (create ? "create" : "interact") + " " + catId + " " + next;
                page.append(Component.literal("  "))
                        .append(clickable(label, color, cmd,
                                "Поставить всей категории: " + (next ? "Разрешено" : "Запрещено")))
                        .append(Component.literal("\n"));
            } else {
                page.append(Component.literal("  §7" + label + "\n"));
            }
            return;
        }

        String common = commonMode(country, keys);
        String label = common == null ? "Разное" : modeShort(common);
        ChatFormatting color = common == null ? ChatFormatting.DARK_GRAY : modeColor(common);
        String next = common == null ? "NONE" : nextMode(common);
        if (canEdit) {
            String cmd = "/c setting " + CountryCommand.q(country.name) + " booksetcat "
                    + (create ? "create" : "interact") + " " + catId + " " + next;
            page.append(Component.literal("  "))
                    .append(clickable("[" + label + "]", color, cmd,
                            "Поставить всей категории: " + modeDisplay(next)))
                    .append(Component.literal("\n"));
        } else {
            page.append(Component.literal("  §7[" + label + "]\n"));
        }
    }

    private static String commonMode(Country country, List<String> keys) {
        String first = null;
        for (String k : keys) {
            String m = country.getModInteractMode(k);
            if (first == null) first = m;
            else if (!first.equals(m)) return null;
        }
        return first;
    }

    private static Boolean commonRolePermission(Country country, String roleName, List<String> keys) {
        Boolean first = null;
        for (String k : keys) {
            boolean v = country.getRolePermission(roleName, k);
            if (first == null) first = v;
            else if (first != v) return null;
        }
        return first;
    }

    private static MutableComponent clickable(String text, ChatFormatting color, String cmd, String hover) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static void appendEntry(MutableComponent page, Country country, String key,
                                    boolean create, String catId, boolean canEdit, String roleName) {
        String display = shortName(CountryManager.modRegistry.getOrDefault(key, key));
        page.append(Component.literal("§0" + display + "\n"));

        if (roleName != null) {
            boolean allowed = country.getRolePermission(roleName, key);
            if (canEdit) {
                String cmd = "/c rank " + CountryCommand.q(country.name) + " rolebookset " + roleName + " "
                        + (create ? "create" : "interact") + " " + catId + " "
                        + CountryCommand.q(key) + " " + (!allowed);
                MutableComponent btn = Component.literal(allowed ? "[Разрешено]" : "[Запрещено]")
                        .withStyle(style -> style
                                .withColor(allowed ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Нажмите, чтобы " + (allowed ? "запретить" : "разрешить")))));
                page.append(Component.literal("  ")).append(btn).append(Component.literal("\n"));
            } else {
                page.append(Component.literal("  " + (allowed ? "§2Разрешено" : "§4Запрещено") + "\n"));
            }
            return;
        }

        String mode = country.getModInteractMode(key);
        if (canEdit) {
            String next = nextMode(mode);
            String cmd = "/c setting " + CountryCommand.q(country.name) + " bookset "
                    + (create ? "create" : "interact") + " " + catId + " "
                    + CountryCommand.q(key) + " " + next;
            MutableComponent btn = Component.literal("[" + modeShort(mode) + "]")
                    .withStyle(style -> style
                            .withColor(modeColor(mode))
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Сейчас: " + modeDisplay(mode) + "\n§7Нажмите, чтобы сменить"))));
            page.append(Component.literal("  ")).append(btn).append(Component.literal("\n"));
        } else {
            page.append(Component.literal("  " + modeDisplay(mode) + "\n"));
        }
    }

    // ---- Сборка и открытие книги ----

    private static ItemStack buildBook(List<MutableComponent> pages, boolean create) {
        List<Filterable<Component>> bookPages = new ArrayList<>(pages.size());
        for (MutableComponent page : pages)
            bookPages.add(Filterable.passThrough(page));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(create ? "Настройки Create" : "Взаимодействия"),
                "SCountry",
                2,
                bookPages,
                true));
        return book;
    }

    private static void openBookItem(ServerPlayer player, ItemStack book) {
        int slot = player.getInventory().selected;
        int containerSlot = 36 + slot;
        ItemStack original = player.getInventory().getItem(slot).copy();
        int stateId = player.inventoryMenu.getStateId();

        player.connection.send(new ClientboundContainerSetSlotPacket(0, stateId, containerSlot, book));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
        player.connection.send(new ClientboundContainerSetSlotPacket(0, stateId, containerSlot, original));
    }

    // ==================== ИЗМЕНЕНИЕ НАСТРОЕК (чат) ====================

    public static void changeSetting(ServerPlayer player, String countryName, String key, String value) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null) { player.sendSystemMessage(err("Страна не найдена!")); return; }

        boolean canEdit = player.hasPermissions(2) || isLeaderOrDeputy(player, country);
        if (!canEdit) { player.sendSystemMessage(err("Нет прав!")); return; }

        if (!value.equals("ALL") && !value.equals("ONLY_MEMBERS") && !value.equals("NONE")) {
            player.sendSystemMessage(err("Допустимые значения: ALL / ONLY_MEMBERS / NONE"));
            return;
        }

        if (key.startsWith("interact.")) {
            String interactKey = key.substring("interact.".length());
            boolean create = isCreateKey(interactKey);
            boolean locked = create ? country.createLocked : country.interactLocked;
            if (!player.hasPermissions(2) && locked) {
                player.sendSystemMessage(err(create
                        ? "Настройка Create заблокирована администратором!"
                        : "Настройка взаимодействий заблокирована администратором!"));
                return;
            }
            if (!CountryManager.modRegistry.containsKey(interactKey)) {
                player.sendSystemMessage(err("Неизвестное взаимодействие: " + interactKey));
                return;
            }
            country.modSettings.put(interactKey, value);
            mgr.saveAll();
            String display = CountryManager.modRegistry.get(interactKey);
            player.sendSystemMessage(Component.literal("§a✅ " + display + " → " + modeDisplay(value)));
            showSettings(player, countryName);
            return;
        }

        if (!player.hasPermissions(2) && country.isSettingLocked(key)) {
            player.sendSystemMessage(err("Эта настройка заблокирована администратором!"));
            return;
        }

        String settingName;
        switch (key) {
            case "pvp":       country.pvpMode = value; settingName = "PVP"; break;
            case "build":
                if (!player.hasPermissions(2) && (country.breakLocked || country.placeLocked)) {
                    player.sendSystemMessage(err("Ломание или постановка заблокированы администратором!"));
                    return;
                }
                country.buildMode = value;
                country.breakMode = value;
                country.placeMode = value;
                settingName = "Ломать и ставить";
                break;
            case "break":     country.breakMode = value;     settingName = "Ломать блоки";    break;
            case "place":     country.placeMode = value;     settingName = "Ставить блоки";   break;
            case "pickup":    country.pickupMode = value;    settingName = "Поднятие";        break;
            case "interact":  country.interactMode = value;  settingName = "Взаимодействие";  break;
            case "explosion": country.explosionMode = value; settingName = "Взрывы";          break;
            default: player.sendSystemMessage(err("Неизвестная настройка: " + key)); return;
        }
        mgr.saveAll();
        player.sendSystemMessage(Component.literal("§a✅ " + settingName + " → " + modeDisplay(value)));
        showSettings(player, countryName);
    }

    public static void changeLock(ServerPlayer player, String countryName, String key, boolean locked) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null) { player.sendSystemMessage(err("Страна не найдена!")); return; }

        switch (key) {
            case "pvp":       country.pvpLocked       = locked; break;
            case "build":     country.buildLocked     = locked; break;
            case "break":     country.breakLocked     = locked; break;
            case "place":     country.placeLocked     = locked; break;
            case "pickup":    country.pickupLocked    = locked; break;
            case "interact":  country.interactLocked  = locked; break;
            case "create":    country.createLocked    = locked; break;
            case "explosion": country.explosionLocked = locked; break;
            default: player.sendSystemMessage(err("Неизвестная настройка: " + key)); return;
        }
        mgr.saveAll();
        player.sendSystemMessage(Component.literal("§a✅ " + key + (locked ? " заблокирована" : " разблокирована")));
        showSettings(player, countryName);
    }

    // ==================== РОЛИ ====================

    public static void showRoleSettings(ServerPlayer player, String countryName, String roleName) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null || !country.hasCustomRole(roleName)) {
            player.sendSystemMessage(err("Роль не найдена!"));
            return;
        }

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6⚙ НАСТРОЙКИ РОЛИ: §e" + roleName));
        player.sendSystemMessage(Component.literal(""));
        sendRoleLine(player, countryName, roleName, country, "⚔ PVP",            "pvp");
        sendRoleLine(player, countryName, roleName, country, "⛏ Ломать блоки",   "break");
        sendRoleLine(player, countryName, roleName, country, "🧱 Ставить блоки", "place");
        sendRoleLine(player, countryName, roleName, country, "🎒 Поднятие",      "pickup");
        sendRoleLine(player, countryName, roleName, country, "🔧 Взаимодействие","interact");
        sendRoleInteractHeader(player, countryName, roleName);
        sendRoleCreateHeader(player, countryName, roleName);
        player.sendSystemMessage(Component.literal(""));
    }

    private static void sendRoleInteractHeader(ServerPlayer player, String countryName, String roleName) {
        MutableComponent arrow = Component.literal("§a▶ ")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/c rank " + CountryCommand.q(countryName) + " roleexpand " + roleName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Открыть книгу взаимодействий роли"))));
        player.sendSystemMessage(Component.literal("").append(arrow)
                .append(Component.literal("§e🔧 Взаимодействия §7(книга)")));
    }

    private static void sendRoleCreateHeader(ServerPlayer player, String countryName, String roleName) {
        MutableComponent arrow = Component.literal("§a▶ ")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/c rank " + CountryCommand.q(countryName) + " rolecreateexpand " + roleName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Открыть книгу взаимодействий Create роли"))));
        player.sendSystemMessage(Component.literal("").append(arrow)
                .append(Component.literal("§6🟧 Create §7(книга)")));
    }

    public static void changeRoleSetting(ServerPlayer player, String countryName, String roleName, String perm, boolean value) {
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country country = mgr.getCountryByName(countryName);
        if (country == null || !country.hasCustomRole(roleName)) {
            player.sendSystemMessage(err("Роль не найдена!"));
            return;
        }
        if (!player.hasPermissions(2) && !isLeaderOrDeputy(player, country)) {
            player.sendSystemMessage(err("Нет прав!"));
            return;
        }
        if (!Country.ROLE_PERM_KEYS.contains(perm)) {
            player.sendSystemMessage(err("Неизвестное право!"));
            return;
        }
        country.setCustomRolePermission(roleName, perm, value);
        mgr.saveAll();
        player.sendSystemMessage(Component.literal("§a✅ " + perm + (value ? " Разрешено" : " Запрещено")));
        showRoleSettings(player, countryName, roleName);
    }

    // ==================== ПРИВАТНЫЕ СТРОИТЕЛИ ====================

    private static void sendSettingLine(ServerPlayer player, Country country, String label, String key, String mode, boolean locked) {
        MutableComponent line = Component.literal("§e" + label + ": ");
        line.append(modeButton(country.name, key, "ONLY_MEMBERS", "[Только жители]", mode));
        line.append(Component.literal(" "));
        line.append(modeButton(country.name, key, "ALL",          "[Все]",            mode));
        line.append(Component.literal(" "));
        line.append(modeButton(country.name, key, "NONE",         "[Никто]",          mode));
        if (locked) line.append(Component.literal(" §c🔒"));
        player.sendSystemMessage(line);
    }

    private static void sendInteractHeader(ServerPlayer player, Country country, boolean canEdit) {
        MutableComponent arrow = Component.literal("§a▶ ")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/c setting " + CountryCommand.q(country.name) + " expand"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Открыть книгу взаимодействий"))));

        MutableComponent label = Component.literal("§e🔧 Взаимодействие §7(книга)");
        if (canEdit && country.interactLocked) label.append(Component.literal(" §c🔒"));

        player.sendSystemMessage(Component.literal("").append(arrow).append(label));
    }

    private static void sendCreateHeader(ServerPlayer player, Country country, boolean canEdit) {
        MutableComponent arrow = Component.literal("§a▶ ")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/c setting " + CountryCommand.q(country.name) + " createexpand"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Открыть книгу взаимодействий Create"))));

        MutableComponent label = Component.literal("§6🟧 Create §7(книга)");
        if (canEdit && country.createLocked) label.append(Component.literal(" §c🔒"));

        player.sendSystemMessage(Component.literal("").append(arrow).append(label));
    }

    private static MutableComponent modeButton(String country, String key, String value, String label, String currentMode) {
        boolean active = value.equals(currentMode);
        return Component.literal(label)
                .withStyle(style -> style
                        .withColor(active ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        .withBold(active)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/c setting " + CountryCommand.q(country) + " " + CountryCommand.q(key) + " " + value))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Установить: " + modeDisplay(value)))));
    }

    private static void sendLockLine(ServerPlayer player, Country country, String key, boolean locked) {
        MutableComponent btn = Component.literal(locked ? "[🔒 Заблокировано]" : "[🔓 Разблокировано]")
                .withStyle(style -> style
                        .withColor(locked ? ChatFormatting.RED : ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/c setting " + CountryCommand.q(country.name) + " lock " + key + " " + !locked)));
        player.sendSystemMessage(Component.literal("§7" + key + ": ").append(btn));
    }

    private static void sendRoleLine(ServerPlayer player, String countryName, String roleName, Country country, String label, String perm) {
        boolean val = country.getRolePermission(roleName, perm);
        String base = "/c rank " + CountryCommand.q(countryName) + " setting " + roleName + " " + perm + " ";
        MutableComponent allow = Component.literal("[Разрешено]")
                .withStyle(style -> style
                        .withColor(val ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        .withBold(val)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, base + "true")));
        MutableComponent deny = Component.literal("[Запрещено]")
                .withStyle(style -> style
                        .withColor(!val ? ChatFormatting.RED : ChatFormatting.GRAY)
                        .withBold(!val)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, base + "false")));
        player.sendSystemMessage(Component.literal("§e" + label + ": ").append(allow).append(" ").append(deny));
    }

    // ==================== УТИЛИТЫ ====================

    public static String modeDisplay(String mode) {
        switch (mode) {
            case "ALL":          return "§aВсе";
            case "ONLY_MEMBERS": return "§eТолько жители";
            case "NONE":         return "§cНикто";
            default:             return mode;
        }
    }

    private static String modeShort(String mode) {
        switch (mode) {
            case "ALL":          return "Все";
            case "ONLY_MEMBERS": return "Жители";
            case "NONE":         return "Никто";
            default:             return mode;
        }
    }

    private static ChatFormatting modeColor(String mode) {
        switch (mode) {
            case "ALL":          return ChatFormatting.DARK_GREEN;
            case "ONLY_MEMBERS": return ChatFormatting.GOLD;
            case "NONE":         return ChatFormatting.DARK_RED;
            default:             return ChatFormatting.BLACK;
        }
    }

    private static String nextMode(String mode) {
        switch (mode) {
            case "ALL":          return "ONLY_MEMBERS";
            case "ONLY_MEMBERS": return "NONE";
            default:             return "ALL";
        }
    }

    private static String shortName(String s) {
        int i = s.indexOf(" (");
        return i > 0 ? s.substring(0, i) : s;
    }

    private static boolean isLeaderOrDeputy(ServerPlayer player, Country country) {
        String role = country.getRole(player.getGameProfile().getName());
        return "Лидер".equals(role) || "Заместитель".equals(role);
    }

    private static MutableComponent err(String msg) {
        return Component.literal("§c" + msg);
    }
}