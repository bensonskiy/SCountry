package com.bensonskiy.scountry.commands;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import com.bensonskiy.scountry.events.BorderViewHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class CountryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildTree("country"));
        dispatcher.register(buildTree("c"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTree(String name) {
        return Commands.literal(name)
                .executes(ctx -> cmdInfo(ctx, null))
                .then(Commands.literal("help").executes(CountryCommand::cmdHelp))
                .then(Commands.literal("info").executes(ctx -> cmdInfo(ctx, null))
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .executes(ctx -> cmdInfo(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("new").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(CountryCommand::cmdNew)))
                .then(Commands.literal("rename").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(CountryCommand::cmdRename)))
                .then(Commands.literal("delete").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .executes(CountryCommand::cmdDelete)))
                .then(Commands.literal("leave")
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .executes(CountryCommand::cmdLeave)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(suggestOnlinePlayers())
                                .executes(ctx -> cmdInvite(ctx, null))
                                .then(Commands.argument("country", StringArgumentType.string())
                                        .suggests(suggestCountries())
                                        .executes(ctx -> cmdInvite(ctx, StringArgumentType.getString(ctx, "country"))))))
                .then(Commands.literal("accept").executes(CountryCommand::cmdAccept))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(suggestOnlinePlayers())
                                .executes(ctx -> cmdKick(ctx, null))
                                .then(Commands.argument("country", StringArgumentType.string())
                                        .suggests(suggestCountries())
                                        .executes(ctx -> cmdKick(ctx, StringArgumentType.getString(ctx, "country"))))))
                .then(Commands.literal("view").executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                    BorderViewHandler.toggleView(p);
                    return 1;
                }))
                .then(Commands.literal("map").executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                            new com.bensonskiy.scountry.network.OpenMapPacket());
                    return 1;
                }))
                .then(cmdSetting())
                .then(cmdRank())
                .then(Commands.literal("region").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .then(Commands.literal("add").executes(ctx -> cmdRegion(ctx, true)))
                                .then(Commands.literal("remove").executes(ctx -> cmdRegion(ctx, false)))))
                .then(Commands.literal("addchunk").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    CountryManager m = mgr();
                                                    if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
                                                    Country c = m.getCountryByName(StringArgumentType.getString(ctx, "country"));
                                                    if (c == null) { fail(ctx, "Страна не найдена!"); return 0; }
                                                    String dim = "overworld";
                                                    if (ctx.getSource().getEntity() instanceof ServerPlayer p)
                                                        dim = CountryManager.dimPath(p.serverLevel());
                                                    m.addChunk(c, dim,
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "z"));
                                                    send(ctx, "§aЧанк добавлен к §e" + c.name);
                                                    return 1;
                                                })))))
                .then(Commands.literal("removechunk").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    CountryManager m = mgr();
                                                    if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
                                                    Country c = m.getCountryByName(StringArgumentType.getString(ctx, "country"));
                                                    if (c == null) { fail(ctx, "Страна не найдена!"); return 0; }
                                                    String dim = "overworld";
                                                    if (ctx.getSource().getEntity() instanceof ServerPlayer p)
                                                        dim = CountryManager.dimPath(p.serverLevel());
                                                    m.removeChunk(c, dim,
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "z"));
                                                    send(ctx, "§aЧанк снят с §e" + c.name);
                                                    return 1;
                                                })))))
                .then(Commands.literal("citizenship").executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                    showCitizenshipMenu(p);
                    return 1;
                }))
                .then(Commands.literal("setmain")
                        .then(Commands.argument("country", StringArgumentType.string())
                                .suggests(suggestCountries())
                                .executes(ctx -> {
                                    ServerPlayer p = player(ctx);
                                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                    CountryManager m = mgr();
                                    if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
                                    String cName = StringArgumentType.getString(ctx, "country");
                                    Country t = m.getCountryByName(cName);
                                    if (t == null || !t.isMember(p.getGameProfile().getName())) {
                                        fail(ctx, "Вы не состоите в этой стране!"); return 0;
                                    }
                                    m.setMainCountry(p.getUUID().toString(), t.name);
                                    send(ctx, "§aОсновное гражданство — §e" + t.name);
                                    return 1;
                                })));
    }

    // ==================== SETTING ====================

    private static LiteralArgumentBuilder<CommandSourceStack> cmdSetting() {
        return Commands.literal("setting")
                .executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                    CountryManager m = mgr();
                    if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
                    Country c = m.getMainCountryOf(p.getUUID().toString(), p.getGameProfile().getName());
                    if (c == null) { fail(ctx, "Вы не в стране!"); return 0; }
                    SettingsUI.showSettings(p, c.name);
                    return 1;
                })
                .then(Commands.argument("country", StringArgumentType.string())
                        .suggests(suggestCountries())
                        .executes(ctx -> {
                            ServerPlayer p = player(ctx);
                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                            SettingsUI.showSettings(p, StringArgumentType.getString(ctx, "country"));
                            return 1;
                        })
                        // expand — книга обычных взаимодействий
                        .then(Commands.literal("expand").executes(ctx -> {
                            ServerPlayer p = player(ctx);
                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                            SettingsUI.openBook(p, StringArgumentType.getString(ctx, "country"), "interact", null);
                            return 1;
                        }))
                        // createexpand — книга взаимодействий Create
                        .then(Commands.literal("createexpand").executes(ctx -> {
                            ServerPlayer p = player(ctx);
                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                            SettingsUI.openBook(p, StringArgumentType.getString(ctx, "country"), "create", null);
                            return 1;
                        }))
                        // booksetcat <type> <cat> <value> — установить режим всей категории
                        .then(Commands.literal("booksetcat")
                                .then(Commands.argument("btype", StringArgumentType.word())
                                        .then(Commands.argument("cat", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.word()).suggests(suggestModeValues())
                                                        .executes(ctx -> {
                                                            ServerPlayer p = player(ctx);
                                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                                            SettingsUI.changeCategoryBook(p,
                                                                    StringArgumentType.getString(ctx, "country"),
                                                                    StringArgumentType.getString(ctx, "btype"),
                                                                    StringArgumentType.getString(ctx, "cat"),
                                                                    StringArgumentType.getString(ctx, "value"));
                                                            return 1;
                                                        })))))
                        // bookset <type> <cat> <key> <value> — установить конкретную настройку
                        .then(Commands.literal("bookset")
                                .then(Commands.argument("btype", StringArgumentType.word())
                                        .then(Commands.argument("cat", StringArgumentType.word())
                                                .then(Commands.argument("bkey", StringArgumentType.string())
                                                        .then(Commands.argument("bvalue", StringArgumentType.word()).suggests(suggestModeValues())
                                                                .executes(ctx -> {
                                                                    ServerPlayer p = player(ctx);
                                                                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                                                    SettingsUI.changeSettingBook(p,
                                                                            StringArgumentType.getString(ctx, "country"),
                                                                            StringArgumentType.getString(ctx, "btype"),
                                                                            StringArgumentType.getString(ctx, "cat"),
                                                                            StringArgumentType.getString(ctx, "bkey"),
                                                                            StringArgumentType.getString(ctx, "bvalue"));
                                                                    return 1;
                                                                }))))))
                        // lock <key> <bool> — только admin
                        .then(Commands.literal("lock").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("lockKey", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String k : new String[]{"pvp","build","break","place","pickup","interact","create","explosion"}) b.suggest(k);
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("lockValue", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    ServerPlayer p = player(ctx);
                                                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                                    SettingsUI.changeLock(p,
                                                            StringArgumentType.getString(ctx, "country"),
                                                            StringArgumentType.getString(ctx, "lockKey"),
                                                            BoolArgumentType.getBool(ctx, "lockValue"));
                                                    return 1;
                                                }))))
                        // <key> <value> — прямое изменение настройки
                        .then(Commands.argument("key", StringArgumentType.string()).suggests(suggestSettingKeys())
                                .then(Commands.argument("value", StringArgumentType.word()).suggests(suggestModeValues())
                                        .executes(ctx -> {
                                            ServerPlayer p = player(ctx);
                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                            SettingsUI.changeSetting(p,
                                                    StringArgumentType.getString(ctx, "country"),
                                                    StringArgumentType.getString(ctx, "key"),
                                                    StringArgumentType.getString(ctx, "value"));
                                            return 1;
                                        }))));
    }

    // ==================== RANK ====================

    private static LiteralArgumentBuilder<CommandSourceStack> cmdRank() {
        return Commands.literal("rank")
                .then(Commands.argument("country", StringArgumentType.string())
                        .suggests(suggestCountries())
                        .then(Commands.literal("create")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(CountryCommand::rankCreate)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(CountryCommand::rankDelete)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(suggestOnlinePlayers())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(CountryCommand::rankAdd))))
                        // roleexpand <role> — книга обычных взаимодействий роли
                        .then(Commands.literal("roleexpand")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer p = player(ctx);
                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                            SettingsUI.openRoleBook(p,
                                                    StringArgumentType.getString(ctx, "country"),
                                                    StringArgumentType.getString(ctx, "role"),
                                                    "interact", null);
                                            return 1;
                                        })))
                        // rolecreateexpand <role> — книга Create-взаимодействий роли
                        .then(Commands.literal("rolecreateexpand")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer p = player(ctx);
                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                            SettingsUI.openRoleBook(p,
                                                    StringArgumentType.getString(ctx, "country"),
                                                    StringArgumentType.getString(ctx, "role"),
                                                    "create", null);
                                            return 1;
                                        })))
                        // rolebookcat <role> <type> <cat> <true|false>
                        .then(Commands.literal("rolebookcat")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .then(Commands.argument("rtype", StringArgumentType.word())
                                                .then(Commands.argument("cat", StringArgumentType.word())
                                                        .then(Commands.argument("rvalue", BoolArgumentType.bool())
                                                                .executes(ctx -> {
                                                                    ServerPlayer p = player(ctx);
                                                                    if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                                                    SettingsUI.changeRoleCategoryBook(p,
                                                                            StringArgumentType.getString(ctx, "country"),
                                                                            StringArgumentType.getString(ctx, "role"),
                                                                            StringArgumentType.getString(ctx, "rtype"),
                                                                            StringArgumentType.getString(ctx, "cat"),
                                                                            BoolArgumentType.getBool(ctx, "rvalue"));
                                                                    return 1;
                                                                }))))))
                        // rolebookset <role> <type> <cat> <key> <true|false>
                        .then(Commands.literal("rolebookset")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .then(Commands.argument("rtype", StringArgumentType.word())
                                                .then(Commands.argument("cat", StringArgumentType.word())
                                                        .then(Commands.argument("rkey", StringArgumentType.string())
                                                                .then(Commands.argument("rvalue", BoolArgumentType.bool())
                                                                        .executes(ctx -> {
                                                                            ServerPlayer p = player(ctx);
                                                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                                                            SettingsUI.changeRoleSettingBook(p,
                                                                                    StringArgumentType.getString(ctx, "country"),
                                                                                    StringArgumentType.getString(ctx, "role"),
                                                                                    StringArgumentType.getString(ctx, "rtype"),
                                                                                    StringArgumentType.getString(ctx, "cat"),
                                                                                    StringArgumentType.getString(ctx, "rkey"),
                                                                                    BoolArgumentType.getBool(ctx, "rvalue"));
                                                                            return 1;
                                                                        })))))))
                        // setting <role> [<perm> <bool>]
                        .then(Commands.literal("setting")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer p = player(ctx);
                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                            SettingsUI.showRoleSettings(p,
                                                    StringArgumentType.getString(ctx, "country"),
                                                    StringArgumentType.getString(ctx, "role"));
                                            return 1;
                                        })
                                        .then(Commands.argument("perm", StringArgumentType.word())
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> {
                                                            ServerPlayer p = player(ctx);
                                                            if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
                                                            SettingsUI.changeRoleSetting(p,
                                                                    StringArgumentType.getString(ctx, "country"),
                                                                    StringArgumentType.getString(ctx, "role"),
                                                                    StringArgumentType.getString(ctx, "perm"),
                                                                    BoolArgumentType.getBool(ctx, "value"));
                                                            return 1;
                                                        }))))));
    }

    // ==================== УТИЛИТА q() ====================

    /**
     * Оборачивает имя страны/ключа в кавычки, если в нём есть пробелы или не-ASCII.
     * Brigadier StringArgumentType.string() без кавычек не принимает такие значения,
     * а из книги настроек мы передаём имя страны прямо в команду.
     */
    public static String q(String name) {
        if (name == null) return "\"\"";
        if (name.matches("[A-Za-z0-9_.+-]+")) return name;
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ==================== HELPERS ====================

    private static CountryManager mgr() { return SCountryServer.countryManager; }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static void fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("§c" + msg));
    }

    private static SuggestionProvider<CommandSourceStack> suggestCountries() {
        return (ctx, b) -> {
            CountryManager m = mgr();
            if (m != null) m.getCountries().keySet().forEach(name -> b.suggest(q(name)));
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestOnlinePlayers() {
        return (ctx, b) -> {
            if (ctx.getSource().getServer() != null)
                ctx.getSource().getServer().getPlayerList().getPlayers()
                        .forEach(p -> b.suggest(p.getGameProfile().getName()));
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestSettingKeys() {
        return (ctx, b) -> {
            for (String k : new String[]{"pvp","build","break","place","pickup","interact","explosion"}) b.suggest(k);
            CountryManager.modRegistry.keySet().forEach(k -> b.suggest(q("interact." + k)));
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestModeValues() {
        return (ctx, b) -> {
            for (String v : new String[]{"ALL","ONLY_MEMBERS","NONE"}) b.suggest(v);
            return b.buildFuture();
        };
    }

    private static boolean isLeaderOrDeputy(ServerPlayer p, Country c) {
        String role = c.getRole(p.getGameProfile().getName());
        return "Лидер".equals(role) || "Заместитель".equals(role);
    }

    // ==================== КОМАНДЫ ====================

    private static int cmdInfo(CommandContext<CommandSourceStack> ctx, String countryArg) {
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        Country country;
        if (countryArg != null) {
            country = m.getCountryByName(countryArg);
            if (country == null) { fail(ctx, "Страна не найдена!"); return 0; }
        } else {
            ServerPlayer p = player(ctx);
            if (p == null) { fail(ctx, "Укажите страну: /c info <название>"); return 0; }
            country = m.getMainCountryOf(p.getUUID().toString(), p.getGameProfile().getName());
            if (country == null) { fail(ctx, "Вы не состоите ни в одной стране."); return 0; }
        }

        send(ctx, "");
        send(ctx, "§6══════════ §e" + country.name + " §6══════════");
        send(ctx, "§e👑 Лидер: §f" + country.leader);
        send(ctx, "§e📦 Чанков: §f" + country.chunks.size());
        send(ctx, "§e👥 Участников: §f" + country.members.size());

        Map<String, List<String>> byRole = new LinkedHashMap<>();
        byRole.put("Лидер", new ArrayList<>());
        byRole.put("Заместитель", new ArrayList<>());
        byRole.put("Участник", new ArrayList<>());
        for (Map.Entry<String, String> e : country.members.entrySet())
            byRole.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        for (Map.Entry<String, List<String>> e : byRole.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            send(ctx, "§e🔹 " + e.getKey() + ": §f" + String.join(", ", e.getValue()));
        }
        send(ctx, "§7══════════════════════════════");
        return 1;
    }

    private static int cmdNew(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "args").trim();
        String name, leader;
        int lt = raw.indexOf('<'), gt = raw.lastIndexOf('>');
        if (lt == 0 && gt > lt) {
            name = raw.substring(lt + 1, gt).trim();
            String rest = raw.substring(gt + 1).trim();
            if (name.isEmpty() || rest.isEmpty()) { fail(ctx, "Использование: /c new <название> <лидер>"); return 0; }
            leader = rest.split("\\s+")[0];
        } else {
            String[] parts = raw.split("\\s+");
            if (parts.length < 2) { fail(ctx, "Использование: /c new <название> <лидер>"); return 0; }
            leader = parts[parts.length - 1];
            name = String.join(" ", Arrays.copyOfRange(parts, 0, parts.length - 1));
        }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        if (m.isPlayerLeaderAnywhere(leader)) { fail(ctx, leader + " уже руководит страной!"); return 0; }
        ServerPlayer lp = ctx.getSource().getServer() != null
                ? ctx.getSource().getServer().getPlayerList().getPlayerByName(leader) : null;
        String uuid = lp != null ? lp.getUUID().toString() : null;
        if (m.createCountry(name, leader, uuid)) {
            send(ctx, "§aСтрана §e" + name + " §aсоздана! Лидер: §e" + leader);
        } else {
            fail(ctx, "Страна уже существует или игрок уже в другой стране!");
        }
        return 1;
    }

    private static int cmdRename(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "args").trim();
        int lt1 = raw.indexOf('<'), gt1 = raw.indexOf('>');
        int lt2 = raw.indexOf('<', gt1 + 1), gt2 = raw.indexOf('>', lt2 + 1);
        if (lt1 != 0 || gt1 < 0 || lt2 < 0 || gt2 < 0) {
            fail(ctx, "Использование: /c rename <Старое Имя> <Новое Имя>"); return 0;
        }
        String oldName = raw.substring(lt1 + 1, gt1).trim();
        String newName = raw.substring(lt2 + 1, gt2).trim();
        if (oldName.isEmpty() || newName.isEmpty()) { fail(ctx, "Пустые имена недопустимы."); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        if (m.getCountryByName(oldName) == null) { fail(ctx, "Страна не найдена!"); return 0; }
        if (m.getCountryByName(newName) != null) { fail(ctx, "Имя занято!"); return 0; }
        if (m.renameCountry(oldName, newName))
            send(ctx, "§aПереименовано: §e" + oldName + " §a→ §e" + newName);
        else fail(ctx, "Не удалось переименовать.");
        return 1;
    }

    private static int cmdDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "country");
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        if (m.getCountryByName(name) == null) { fail(ctx, "Страна не найдена!"); return 0; }
        m.deleteCountry(name);
        send(ctx, "§aСтрана §e" + name + " §aудалена.");
        return 1;
    }

    private static int cmdLeave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        String name = StringArgumentType.getString(ctx, "country");
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        Country c = m.getCountryByName(name);
        if (c == null || !c.isMember(p.getGameProfile().getName())) {
            fail(ctx, "Вы не в этой стране!"); return 0;
        }
        if (c.leader.equalsIgnoreCase(p.getGameProfile().getName())) {
            fail(ctx, "Лидер не может покинуть страну!"); return 0;
        }
        m.removeMember(c, p.getGameProfile().getName(), p.getUUID().toString());
        send(ctx, "§aВы покинули страну §e" + name + "§a.");
        return 1;
    }

    private static int cmdInvite(CommandContext<CommandSourceStack> ctx, String countryArg) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        String inviteeName = StringArgumentType.getString(ctx, "player");
        Country country;
        if (countryArg != null) {
            country = m.getCountryByName(countryArg);
            if (country == null) { fail(ctx, "Страна не найдена!"); return 0; }
        } else {
            country = m.getMainCountryOf(p.getUUID().toString(), p.getGameProfile().getName());
            if (country == null) { fail(ctx, "Вы не в стране!"); return 0; }
        }
        if (!p.hasPermissions(2) && !country.roleAllows(p.getGameProfile().getName(), "invite")) {
            fail(ctx, "У вас нет права приглашать игроков!"); return 0;
        }
        if (country.isMember(inviteeName)) {
            fail(ctx, inviteeName + " уже в стране!"); return 0;
        }
        country.addInvite(inviteeName);
        m.saveAll();
        send(ctx, "§aПриглашение отправлено §e" + inviteeName + "§a.");
        ServerPlayer inv = ctx.getSource().getServer() != null
                ? ctx.getSource().getServer().getPlayerList().getPlayerByName(inviteeName) : null;
        if (inv != null) inv.sendSystemMessage(Component.literal(
                "§6Вас пригласили в страну §e" + country.name + "§6. Введите §f/c accept§6."));
        return 1;
    }

    private static int cmdAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        String name = p.getGameProfile().getName();
        Country found = null;
        for (Country c : m.getCountries().values()) if (c.hasInvite(name)) { found = c; break; }
        if (found == null) { fail(ctx, "У вас нет приглашений."); return 0; }
        found.removeInvite(name);
        m.addMember(found, name, "Участник", p.getUUID().toString());
        send(ctx, "§aВы вступили в страну §e" + found.name + "§a!");
        return 1;
    }

    private static int cmdKick(CommandContext<CommandSourceStack> ctx, String countryArg) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        String target = StringArgumentType.getString(ctx, "player");
        Country country;
        if (countryArg != null) {
            country = m.getCountryByName(countryArg);
            if (country == null) { fail(ctx, "Страна не найдена!"); return 0; }
        } else {
            country = m.getMainCountryOf(p.getUUID().toString(), p.getGameProfile().getName());
            if (country == null) { fail(ctx, "Вы не в стране!"); return 0; }
        }
        if (!p.hasPermissions(2) && !country.roleAllows(p.getGameProfile().getName(), "kick")) {
            fail(ctx, "У вас нет права исключать игроков!"); return 0;
        }
        if (!country.isMember(target)) { fail(ctx, "Игрок не в стране!"); return 0; }
        if (country.leader.equalsIgnoreCase(target) && !p.hasPermissions(2)) {
            fail(ctx, "Нельзя исключить лидера!"); return 0;
        }
        ServerPlayer tp = ctx.getSource().getServer() != null
                ? ctx.getSource().getServer().getPlayerList().getPlayerByName(target) : null;
        String uuid = tp != null ? tp.getUUID().toString() : null;
        m.removeMember(country, target, uuid);
        send(ctx, "§a✅ §e" + target + " §aисключён из §e" + country.name + "§a.");
        if (tp != null) tp.sendSystemMessage(Component.literal("§cВас исключили из страны §e" + country.name + "§c."));
        return 1;
    }

    private static int cmdRegion(CommandContext<CommandSourceStack> ctx, boolean add) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        Country c = m.getCountryByName(StringArgumentType.getString(ctx, "country"));
        if (c == null) { fail(ctx, "Страна не найдена!"); return 0; }
        if (!p.hasPermissions(2) && !c.roleAllows(p.getGameProfile().getName(), "claim")) {
            fail(ctx, "У вас нет права управлять территорией!"); return 0;
        }
        String dim = CountryManager.dimPath(p.serverLevel());
        int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4;
        if (add) {
            Country owner = m.getCountryByChunk(dim, cx, cz);
            if (owner != null && owner != c) { fail(ctx, "Чанк принадлежит " + owner.name); return 0; }
            m.addChunk(c, dim, cx, cz);
            send(ctx, "§a✅ Чанк §e" + cx + "," + cz + " §aдобавлен к §e" + c.name);
        } else {
            if (!c.hasChunk(dim, cx, cz)) { fail(ctx, "Чанк не принадлежит стране!"); return 0; }
            m.removeChunk(c, dim, cx, cz);
            send(ctx, "§a✅ Чанк §e" + cx + "," + cz + " §aудалён из §e" + c.name);
        }
        return 1;
    }

    private static int rankCreate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        Country c = m.getCountryByName(StringArgumentType.getString(ctx, "country"));
        if (c == null) { fail(ctx, "Страна не найдена!"); return 0; }
        if (!p.hasPermissions(2) && !isLeaderOrDeputy(p, c)) { fail(ctx, "Нет прав!"); return 0; }
        String r = StringArgumentType.getString(ctx, "role");
        if (c.hasCustomRole(r)) { fail(ctx, "Роль уже есть!"); return 0; }
        c.createCustomRole(r);
        m.saveAll();
        send(ctx, "§aРоль §e" + r + " §aсоздана!");
        return 1;
    }

    private static int rankDelete(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        Country c = m.getCountryByName(StringArgumentType.getString(ctx, "country"));
        if (c == null) { fail(ctx, "Страна не найдена!"); return 0; }
        if (!p.hasPermissions(2) && !isLeaderOrDeputy(p, c)) { fail(ctx, "Нет прав!"); return 0; }
        String r = StringArgumentType.getString(ctx, "role");
        if (!c.hasCustomRole(r)) { fail(ctx, "Роль не найдена!"); return 0; }
        c.deleteCustomRole(r);
        m.saveAll();
        send(ctx, "§aРоль §e" + r + " §aудалена!");
        return 1;
    }

    private static int rankAdd(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) { fail(ctx, "Только для игроков!"); return 0; }
        CountryManager m = mgr();
        if (m == null) { fail(ctx, "Данные не загружены"); return 0; }
        Country c = m.getCountryByName(StringArgumentType.getString(ctx, "country"));
        if (c == null) { fail(ctx, "Страна не найдена!"); return 0; }
        if (!p.hasPermissions(2) && !isLeaderOrDeputy(p, c)) { fail(ctx, "Нет прав!"); return 0; }
        String target = StringArgumentType.getString(ctx, "player");
        String role = StringArgumentType.getString(ctx, "role");

        if ("Лидер".equals(role)) {
            Country led = m.getLedCountry(target);
            if (led != null && led != c) { fail(ctx, target + " уже руководит " + led.name + "!"); return 0; }
        }
        if (!c.isMember(target)) {
            ServerPlayer tp = ctx.getSource().getServer().getPlayerList().getPlayerByName(target);
            String uuid = tp != null ? tp.getUUID().toString() : null;
            m.addMember(c, target, role, uuid);
        } else {
            if ("Лидер".equals(role)) {
                if (!p.hasPermissions(2) && !"Лидер".equals(c.getRole(p.getGameProfile().getName()))) {
                    fail(ctx, "Только лидер может передать власть!"); return 0;
                }
                c.setLeader(target);
                m.saveAll();
            } else {
                c.setRole(target, role);
                m.saveAll();
            }
        }
        send(ctx, "§a✅ §e" + target + " §a→ §e" + role);
        return 1;
    }

    private static void showCitizenshipMenu(ServerPlayer p) {
        CountryManager m = SCountryServer.countryManager;
        if (m == null) return;
        String pname = p.getGameProfile().getName();
        String uuid = p.getUUID().toString();

        List<Country> list = m.getCountriesOfPlayer(pname);
        if (list.isEmpty()) { p.sendSystemMessage(Component.literal("§cВы не состоите ни в одной стране.")); return; }

        String mainName = m.getMainCountry(uuid);
        if (mainName == null) {
            mainName = list.get(0).name;
            m.setMainCountry(uuid, mainName);
        }

        p.sendSystemMessage(Component.literal("§6══ Ваше гражданство ══"));
        for (Country c : list) {
            boolean isMain = c.name.equals(mainName);
            p.sendSystemMessage(Component.literal((isMain ? "§a★ " : "§7• ") + c.name + (isMain ? " §a(Основное)" : "")));
            if (!isMain && !c.leader.equalsIgnoreCase(pname)) {
                MutableComponent btn = Component.literal("§b   [Сделать основным]")
                        .withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/c setmain " + q(c.name))));
                p.sendSystemMessage(btn);
            }
        }
    }

    private static int cmdHelp(CommandContext<CommandSourceStack> ctx) {
        send(ctx, "§6════ SCountry ════");
        send(ctx, "§e/c info [страна]§7 — информация");
        send(ctx, "§e/c leave <страна>§7 — покинуть");
        send(ctx, "§e/c invite <ник> [страна]§7 — пригласить");
        send(ctx, "§e/c accept§7 — принять приглашение");
        send(ctx, "§e/c kick <ник> [страна]§7 — исключить");
        send(ctx, "§e/c view§7 — показать границы (частицы)");
        send(ctx, "§e/c map§7 — открыть карту (клавиша ] / ъ)");
        send(ctx, "§e/c setting [страна]§7 — настройки страны");
        send(ctx, "§e/c citizenship§7 — гражданства");
        send(ctx, "§e/c setmain <страна>§7 — основное гражданство");
        send(ctx, "§e/c rank <страна> create|delete|add|setting ...§7 — роли");
        send(ctx, "§6── Админам: /c new, /c rename, /c delete, /c region, /c addchunk, /c removechunk ──");
        return 1;
    }
}