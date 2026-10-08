package com.bensonskiy.scountry.data;

import com.bensonskiy.scountry.SCountry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Менеджер всех стран. JSON-хранилище: <server>/config/scountry/countries.json
 */
public class CountryManager {
    private static final Logger LOGGER = LogManager.getLogger("SCountry");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File dataFile;

    private Map<String, Country> countries = new HashMap<>();
    private Map<String, Set<String>> playerCountries = new HashMap<>();
    private Map<String, String> playerMainCountry = new HashMap<>();

    /** Реестр модовых взаимодействий: ключ → читаемое имя. */
    public static final LinkedHashMap<String, String> modRegistry = new LinkedHashMap<>();

    private static final Set<String> REMOVED_INTERACTIONS =
            new HashSet<>(Arrays.asList("dye", "write", "shield"));

    private static final int[] COLOR_PALETTE = {
            0xFF0000, 0x0000FF, 0x008000, 0xFFFF00, 0x800080,
            0xFFA500, 0x00FFFF, 0x00FF00, 0xFF00FF, 0x800000,
            0x000080, 0x808000, 0x008080, 0xC0C0C0, 0xFFFFFF
    };
    private int colorIndex = 0;

    /** Флаг «данные изменились — надо разослать клиентам». */
    private static volatile boolean dirty = false;
    public static boolean consumeDirty() {
        if (dirty) { dirty = false; return true; }
        return false;
    }

    private int nextColor() {
        int c = COLOR_PALETTE[colorIndex % COLOR_PALETTE.length];
        colorIndex++;
        return c;
    }

    // ==================== ЗАГРУЗКА / СОХРАНЕНИЕ ====================

    private static class SaveData {
        Map<String, Country> countries = new HashMap<>();
        Map<String, String>  playerMainCountry = new HashMap<>();
        Map<String, String>  modRegistry = new LinkedHashMap<>();
        int colorIndex = 0;
    }

    public CountryManager(File dataFile) {
        this.dataFile = dataFile;
        registerVanillaInteractions();
        registerCreateInteractions();
        load();
    }

    private static void registerVanillaInteractions() {
        modRegistry.put("eat",        "🍎 Еда");
        modRegistry.put("enderpearl", "🟣 Эндер жемчуг");
        modRegistry.put("bucket",     "🪣 Ведро");
        modRegistry.put("fire",       "🔥 Кремень и огниво");
        modRegistry.put("bonemeal",   "🌱 Костная мука");
        modRegistry.put("fishing",    "🎣 Удочка");
        modRegistry.put("shears",     "✂ Ножницы");
        modRegistry.put("lead",       "🔗 Поводок");
        modRegistry.put("nametag",    "🏷 Бирка");
        modRegistry.put("endereye",   "👁 Око Эндера");
        modRegistry.put("throw",      "🥚 Метательное");
        modRegistry.put("potion",     "🧪 Зелья (броски)");
        modRegistry.put("drink",      "🍶 Питьё зелий");
        modRegistry.put("xp_bottle",  "🔮 Бутыль опыта");
        modRegistry.put("firework",   "🎆 Фейерверк");
        modRegistry.put("ranged",     "🏹 Лук/Арбалет");
        modRegistry.put("trident",    "🔱 Трезубец");

        modRegistry.put("vanilla:containers",   "📦 Сундуки и хранилища");
        modRegistry.put("vanilla:doors",        "🚪 Двери, люки, калитки");
        modRegistry.put("vanilla:redstone",     "🔴 Кнопки, рычаги, плиты");
        modRegistry.put("vanilla:workstations", "🛠 Верстак, наковальня");
        modRegistry.put("vanilla:beds",         "🛏 Кровати");
    }

    public static String vanillaBlockKey(String blockId) {
        if (blockId == null || !blockId.startsWith("minecraft:")) return null;
        String id = blockId.substring("minecraft:".length());

        if (id.equals("chest") || id.equals("trapped_chest") || id.equals("ender_chest")
                || id.equals("barrel") || id.endsWith("shulker_box") || id.equals("hopper")
                || id.equals("dropper") || id.equals("dispenser") || id.equals("furnace")
                || id.equals("blast_furnace") || id.equals("smoker") || id.equals("brewing_stand")
                || id.equals("crafter") || id.equals("decorated_pot") || id.equals("chiseled_bookshelf")
                || id.equals("beacon"))
            return "vanilla:containers";

        if (id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("_fence_gate"))
            return "vanilla:doors";

        if (id.endsWith("_button") || id.equals("lever") || id.endsWith("_pressure_plate")
                || id.equals("repeater") || id.equals("comparator") || id.equals("note_block")
                || id.equals("daylight_detector"))
            return "vanilla:redstone";

        if (id.equals("crafting_table") || id.equals("anvil") || id.equals("chipped_anvil")
                || id.equals("damaged_anvil") || id.equals("enchanting_table") || id.equals("grindstone")
                || id.equals("stonecutter") || id.equals("loom") || id.equals("cartography_table")
                || id.equals("smithing_table") || id.equals("lectern") || id.equals("composter"))
            return "vanilla:workstations";

        if (id.endsWith("_bed")) return "vanilla:beds";
        return null;
    }

    private static void registerCreateInteractions() {
        modRegistry.put("create:wrench",                     "🔧 Гаечный ключ");
        modRegistry.put("create:rotation_speed_controller",  "⚙ Регулятор скорости");
        modRegistry.put("create:creative_motor",             "⚙ Творческий двигатель");
        modRegistry.put("create:adjustable_chain_gearshift", "⚙ Регулируемая цепная КП");
        modRegistry.put("create:analog_lever",               "🎚 Аналоговый рычаг");
        modRegistry.put("create:weighted_ejector",           "🎯 Весовая катапульта");
        modRegistry.put("create:super_glue",                 "🧷 Суперклей");
        modRegistry.put("create:redstone_contact",           "📇 Редстоуновый контакт");
        modRegistry.put("create:redstone_link",              "📡 Передатчик сигнала");
        modRegistry.put("create:content_observer",           "📖 Умный наблюдатель");
        modRegistry.put("create:stockpile_switch",           "📊 Пороговый переключатель");
        modRegistry.put("create:smart_chute",                "📦 Умный жёлоб");
        modRegistry.put("create:smart_fluid_pipe",           "🚰 Умная жидкостная труба");
        modRegistry.put("create:brass_tunnel",               "🟡 Латунный туннель");
        modRegistry.put("create:item_hatch",                 "📥 Складской люк");
        modRegistry.put("create:deployer",                   "🦾 Автономный активатор");
        modRegistry.put("create:mechanical_arm",             "🦾 Механическая рука");
        modRegistry.put("create:mechanical_crafter",         "🛠 Механический сборщик");
        modRegistry.put("create:sequenced_gearshift",        "⚙ Последовательная КП");
        modRegistry.put("create:schematicannon",             "🔫 Строительная пушка");
        modRegistry.put("create:elevator_contact",           "🛗 Лифтовой контакт");
        modRegistry.put("create:latches",                    "🔀 Редстоуновые триггеры");
        modRegistry.put("create:timers",                     "⏱ Импульсные блоки");
        modRegistry.put("create:funnels",                    "🔽 Шлюзы");
        modRegistry.put("create:gauges",                     "📻 Измерители");
        modRegistry.put("create:displays",                   "🖥 Дисплеи");
        modRegistry.put("create:interfaces",                 "🔌 Портативные интерфейсы");
    }

    private static final Map<String, String> GROUP_MAP = new HashMap<>();
    static {
        GROUP_MAP.put("create:powered_latch",              "create:latches");
        GROUP_MAP.put("create:powered_toggle_latch",       "create:latches");
        GROUP_MAP.put("create:pulse_repeater",             "create:timers");
        GROUP_MAP.put("create:pulse_extender",             "create:timers");
        GROUP_MAP.put("create:brass_funnel",               "create:funnels");
        GROUP_MAP.put("create:andesite_funnel",            "create:funnels");
        GROUP_MAP.put("create:speedometer",                "create:gauges");
        GROUP_MAP.put("create:stressometer",               "create:gauges");
        GROUP_MAP.put("create:display_link",               "create:displays");
        GROUP_MAP.put("create:display_board",              "create:displays");
        GROUP_MAP.put("create:nixie_tube",                 "create:displays");
        GROUP_MAP.put("create:placard",                    "create:displays");
        GROUP_MAP.put("create:portable_storage_interface", "create:interfaces");
        GROUP_MAP.put("create:portable_fluid_interface",   "create:interfaces");
    }

    public static String groupKey(String key) {
        return key == null ? null : GROUP_MAP.getOrDefault(key, key);
    }

    public void load() {
        if (!dataFile.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            SaveData data = gson.fromJson(r, SaveData.class);
            if (data == null) return;
            if (data.countries != null) {
                countries = data.countries;
                playerCountries.clear();
                for (Map.Entry<String, Country> e : countries.entrySet()) {
                    Country country = e.getValue();
                    if (country == null) continue;
                    country.name = e.getKey();
                    if (country.members == null) country.members = new HashMap<>();
                    if (country.chunks == null) country.chunks = new HashSet<>();
                    if (country.pendingInvites == null) country.pendingInvites = new HashSet<>();
                    if (country.modSettings == null) country.modSettings = new HashMap<>();
                    if (country.customRoles == null) country.customRoles = new LinkedHashMap<>();
                    if (country.leader == null || country.leader.isBlank()) {
                        LOGGER.warn("[SCountry] У страны {} отсутствует лидер.", e.getKey());
                    }
                    for (String member : country.members.keySet()) {
                        if (member != null && !member.isBlank()) indexAdd(member, e.getKey());
                    }
                }
            }
            if (data.playerMainCountry != null) playerMainCountry = data.playerMainCountry;
            colorIndex = data.colorIndex;
            if (data.modRegistry != null) data.modRegistry.forEach(modRegistry::putIfAbsent);
            modRegistry.keySet().removeAll(REMOVED_INTERACTIONS);
            modRegistry.keySet().removeAll(GROUP_MAP.keySet());
        } catch (Exception e) {
            LOGGER.error("[SCountry] Ошибка загрузки данных: " + e.getMessage());
        }
        LOGGER.info("[SCountry] Загружено стран: " + countries.size() + ", взаимодействий: " + modRegistry.size());
    }

    public synchronized void saveAll() {
        dirty = true;
        try {
            File dir = dataFile.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                LOGGER.error("[SCountry] Не удалось создать папку данных: {}", dir.getAbsolutePath());
                return;
            }
            SaveData data = new SaveData();
            data.countries = countries;
            data.playerMainCountry = playerMainCountry;
            data.modRegistry = new LinkedHashMap<>(modRegistry);
            data.colorIndex = colorIndex;
            File tmp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(data, w);
            }
            try {
                Files.move(tmp.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("[SCountry] Ошибка сохранения в {}: {}", dataFile.getAbsolutePath(), e.toString());
        }
    }

    // ==================== УПРАВЛЕНИЕ ====================

    public synchronized boolean createCountry(String name, String leaderName, String leaderUUID) {
        if (countries.containsKey(name)) return false;
        if (isPlayerLeaderAnywhere(leaderName)) return false;
        Country c = new Country(name, leaderName);
        c.color = nextColor();
        countries.put(name, c);
        indexAdd(leaderName, name);
        if (leaderUUID != null) playerMainCountry.put(leaderUUID, name);
        saveAll();
        return true;
    }

    public synchronized void deleteCountry(String name) {
        Country c = countries.get(name);
        if (c == null) return;
        for (String m : c.members.keySet()) indexRemove(m, name);
        countries.remove(name);
        playerMainCountry.entrySet().removeIf(e -> name.equals(e.getValue()));
        saveAll();
    }

    public synchronized boolean renameCountry(String oldName, String newName) {
        if (newName == null) return false;
        newName = newName.trim();
        if (newName.isEmpty()) return false;
        Country c = countries.get(oldName);
        if (c == null || oldName.equals(newName)) return false;
        if (countries.containsKey(newName)) return false;
        countries.remove(oldName);
        c.name = newName;
        countries.put(newName, c);
        for (Set<String> set : playerCountries.values())
            if (set.remove(oldName)) set.add(newName);
        for (Map.Entry<String, String> e : playerMainCountry.entrySet())
            if (oldName.equals(e.getValue())) e.setValue(newName);
        saveAll();
        return true;
    }

    public synchronized boolean attachCountry(String sourceName, String targetName) {
        Country src = countries.get(sourceName), tgt = countries.get(targetName);
        if (src == null || tgt == null || src == tgt) return false;
        src.transferChunksTo(tgt);
        for (String m : src.members.keySet()) indexRemove(m, sourceName);
        countries.remove(sourceName);
        playerMainCountry.entrySet().removeIf(e -> sourceName.equals(e.getValue()));
        saveAll();
        return true;
    }

    // ==================== ИНДЕКС ====================

    private void indexAdd(String playerName, String countryName) {
        playerCountries.computeIfAbsent(playerName.toLowerCase(), k -> new LinkedHashSet<>()).add(countryName);
    }

    private void indexRemove(String playerName, String countryName) {
        Set<String> set = playerCountries.get(playerName.toLowerCase());
        if (set == null) return;
        set.remove(countryName);
        if (set.isEmpty()) playerCountries.remove(playerName.toLowerCase());
    }

    public List<Country> getCountriesOfPlayer(String playerName) {
        Set<String> names = playerCountries.get(playerName.toLowerCase());
        if (names == null) return Collections.emptyList();
        List<Country> out = new ArrayList<>(names.size());
        for (String n : names) {
            Country c = countries.get(n);
            if (c != null) out.add(c);
        }
        return out;
    }

    public synchronized void addMember(Country country, String playerName, String role, String uuid) {
        if (country.isMember(playerName)) return;
        country.members.put(playerName, role);
        indexAdd(playerName, country.name);
        if (uuid != null) playerMainCountry.putIfAbsent(uuid, country.name);
        saveAll();
    }

    public synchronized void removeMember(Country country, String playerName, String uuid) {
        country.removeMember(playerName);
        indexRemove(playerName, country.name);
        if (uuid != null && country.name.equals(playerMainCountry.get(uuid))) {
            List<Country> rest = getCountriesOfPlayer(playerName);
            if (rest.isEmpty()) playerMainCountry.remove(uuid);
            else playerMainCountry.put(uuid, rest.get(0).name);
        }
        saveAll();
    }

    public boolean isPlayerInAnyCountry(String playerName) {
        return !getCountriesOfPlayer(playerName).isEmpty();
    }

    public boolean isPlayerLeaderAnywhere(String playerName) {
        return getLedCountry(playerName) != null;
    }

    public Country getLedCountry(String playerName) {
        for (Country c : getCountriesOfPlayer(playerName))
            if (c.leader != null && c.leader.equalsIgnoreCase(playerName)) return c;
        return null;
    }

    public Country getMainCountryOf(String uuid, String playerName) {
        List<Country> all = getCountriesOfPlayer(playerName);
        if (all.isEmpty()) return null;
        if (uuid != null) {
            String mainName = playerMainCountry.get(uuid);
            if (mainName != null)
                for (Country c : all) if (c.name.equals(mainName)) return c;
        }
        return all.get(0);
    }

    // ==================== ПОИСК ====================

    public Country getCountryByName(String name) { return countries.get(name); }

    public Country getCountryByChunk(String dim, int x, int z) {
        for (Country c : countries.values()) if (c.hasChunk(dim, x, z)) return c;
        return null;
    }

    public String getMainCountry(String uuid)              { return playerMainCountry.get(uuid); }
    public synchronized boolean setMainCountry(String uuid, String name) {
        if (uuid == null || name == null || !countries.containsKey(name)) return false;
        playerMainCountry.put(uuid, name);
        saveAll();
        return true;
    }

    // ==================== ЧАНКИ ====================

    public synchronized void addChunk(Country c, String dim, int x, int z)    { c.addChunk(dim, x, z);    saveAll(); }
    public synchronized void removeChunk(Country c, String dim, int x, int z) { c.removeChunk(dim, x, z); saveAll(); }

    public synchronized List<int[]> addChunks(Country c, String dim, List<int[]> cells) {
        List<int[]> applied = new ArrayList<>();
        if (c == null || dim == null || cells == null || cells.isEmpty()) return applied;
        for (int[] cell : cells) {
            int x = cell[0], z = cell[1];
            if (c.hasChunk(dim, x, z)) continue;
            Country owner = getCountryByChunk(dim, x, z);
            if (owner != null && owner != c) continue;
            c.addChunk(dim, x, z);
            applied.add(cell);
        }
        if (!applied.isEmpty()) saveAll();
        return applied;
    }

    public synchronized List<int[]> removeChunks(Country c, String dim, List<int[]> cells) {
        List<int[]> applied = new ArrayList<>();
        if (c == null || dim == null || cells == null || cells.isEmpty()) return applied;
        for (int[] cell : cells) {
            int x = cell[0], z = cell[1];
            if (!c.hasChunk(dim, x, z)) continue;
            c.removeChunk(dim, x, z);
            applied.add(cell);
        }
        if (!applied.isEmpty()) saveAll();
        return applied;
    }

    public Map<String, Country> getCountries() { return countries; }

    public synchronized boolean isChunkFarFromOthers(String dim, int cx, int cz, int minGap, Country exclude) {
        String prefix = dim + ";";
        for (Country c : countries.values()) {
            if (c == exclude) continue;
            for (String key : c.chunks) {
                if (!key.startsWith(prefix)) continue;
                String[] parts = key.split(";");
                if (parts.length != 3) continue;
                try {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) < minGap) return false;
                } catch (NumberFormatException ignored) {}
            }
        }
        return true;
    }

    public synchronized Country createClaimBlockCountry(String leaderName, String leaderUUID,
                                                        String dim, int cx, int cz, int half, int minGap) {
        if (isPlayerInAnyCountry(leaderName)) return null;
        if (!isChunkFarFromOthers(dim, cx, cz, minGap, null)) return null;
        String name = generateUniqueName(leaderName);
        Country c = new Country(name, leaderName);
        c.color = nextColor();
        countries.put(name, c);
        indexAdd(leaderName, name);
        if (leaderUUID != null) playerMainCountry.put(leaderUUID, name);
        for (int x = cx - half; x <= cx + half; x++)
            for (int z = cz - half; z <= cz + half; z++)
                c.addChunk(dim, x, z);
        saveAll();
        return c;
    }

    private String generateUniqueName(String base) {
        if (!countries.containsKey(base)) return base;
        for (int i = 1; ; i++) {
            String candidate = base + "-" + i;
            if (!countries.containsKey(candidate)) return candidate;
        }
    }

    public static String dimPath(net.minecraft.server.level.ServerLevel level) {
        return level.dimension().location().getPath();
    }
}