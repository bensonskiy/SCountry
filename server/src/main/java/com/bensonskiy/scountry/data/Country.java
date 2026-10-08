package com.bensonskiy.scountry.data;

import java.util.*;

/** Модель страны. Все поля публичны для Gson-сериализации. */
public class Country {
    public String name;
    public String leader;
    public int color = 0xFFFF00;

    // Режимы: ALL / ONLY_MEMBERS / NONE
    public String pvpMode       = "ONLY_MEMBERS";
    public String buildMode     = "ONLY_MEMBERS";
    public String pickupMode    = "ONLY_MEMBERS";
    public String interactMode  = "ONLY_MEMBERS";
    public String explosionMode = "NONE";

    // Раздельные ломание/постановка. null = «как buildMode».
    public String breakMode = null;
    public String placeMode = null;

    public String getBreakMode() { return breakMode != null ? breakMode : buildMode; }
    public String getPlaceMode() { return placeMode != null ? placeMode : buildMode; }

    // Блокировки (только admin)
    public boolean pvpLocked, buildLocked, pickupLocked, interactLocked, explosionLocked;
    public boolean breakLocked, placeLocked, createLocked;

    // Ник → роль
    public Map<String, String> members = new HashMap<>();
    // "dim;x;z"
    public Set<String> chunks = new HashSet<>();
    public Set<String> pendingInvites = new HashSet<>();
    // Модовые взаимодействия: "create:speed_controller" → "ALL/ONLY_MEMBERS/NONE"
    public Map<String, String> modSettings = new HashMap<>();
    // Кастомные роли: имя роли → ключ права → разрешено
    public Map<String, Map<String, Boolean>> customRoles = new LinkedHashMap<>();

    /** Полный список прав роли. Порядок соответствует отображению в GUI. */
    public static final List<String> ROLE_PERM_KEYS = List.of(
            "invite",     // приглашать игроков
            "kick",       // исключать игроков
            "claim",      // управлять территорией (добавлять/снимать чанки)
            "pvp",        // PvP
            "build",      // строительство (общий флаг для break+place)
            "break",      // ломать блоки
            "place",      // ставить блоки
            "pickup",     // подбирать предметы
            "interact",   // взаимодействие с блоками/сущностями
            "explosion"   // взрывы
    );

    public Country() {}

    public Country(String name, String leader) {
        this.name = name;
        this.leader = leader;
        members.put(leader, "Лидер");
    }

    // ==================== ЧЛЕНЫ ====================

    public boolean isMember(String playerName) {
        for (String k : members.keySet()) if (k.equalsIgnoreCase(playerName)) return true;
        return false;
    }

    public String getRole(String playerName) {
        for (Map.Entry<String, String> e : members.entrySet())
            if (e.getKey().equalsIgnoreCase(playerName)) return e.getValue();
        return null;
    }

    public void setRole(String playerName, String role) {
        for (String k : new ArrayList<>(members.keySet()))
            if (k.equalsIgnoreCase(playerName)) { members.put(k, role); return; }
    }

    public void removeMember(String playerName) {
        members.keySet().removeIf(k -> k.equalsIgnoreCase(playerName));
    }

    public void setLeader(String newLeader) {
        setRole(leader, "Участник");
        if (isMember(newLeader)) setRole(newLeader, "Лидер");
        else members.put(newLeader, "Лидер");
        this.leader = newLeader;
    }

    // ==================== ПРИГЛАШЕНИЯ ====================

    public void addInvite(String playerName) {
        if (playerName == null || hasInvite(playerName)) return;
        pendingInvites.add(playerName);
    }

    public boolean hasInvite(String playerName) {
        if (playerName == null) return false;
        for (String k : pendingInvites) if (k.equalsIgnoreCase(playerName)) return true;
        return false;
    }

    public void removeInvite(String playerName) {
        if (playerName == null) return;
        pendingInvites.removeIf(k -> k.equalsIgnoreCase(playerName));
    }

    // ==================== ЧАНКИ ====================

    public boolean hasChunk(String dim, int x, int z) { return chunks.contains(dim + ";" + x + ";" + z); }
    public void addChunk(String dim, int x, int z)    { chunks.add(dim + ";" + x + ";" + z); }
    public void removeChunk(String dim, int x, int z) { chunks.remove(dim + ";" + x + ";" + z); }

    public void transferChunksTo(Country target) {
        target.chunks.addAll(chunks);
        chunks.clear();
    }

    // ==================== НАСТРОЙКИ СТРАНЫ ====================

    public boolean isSettingLocked(String setting) {
        switch (setting) {
            case "pvp":       return pvpLocked;
            case "build":     return buildLocked;
            case "break":     return breakLocked;
            case "place":     return placeLocked;
            case "pickup":    return pickupLocked;
            case "interact":  return interactLocked;
            case "create":    return createLocked;
            case "explosion": return explosionLocked;
            default:          return false;
        }
    }

    public String getModInteractMode(String key) {
        return modSettings.getOrDefault(key, interactMode);
    }

    // ==================== РОЛИ ====================

    public void createCustomRole(String roleName) {
        if (!customRoles.containsKey(roleName)) {
            Map<String, Boolean> p = new LinkedHashMap<>();
            for (String k : ROLE_PERM_KEYS) p.put(k, true);
            customRoles.put(roleName, p);
        }
    }

    private static boolean isBasePerm(String key) {
        return ROLE_PERM_KEYS.contains(key);
    }

    public boolean getRolePermission(String roleName, String permKey) {
        Map<String, Boolean> perms = customRoles.get(roleName);
        if (perms == null || permKey == null) return true;
        Boolean v = perms.get(permKey);
        if (v != null) return v;

        // Фолбэки: break/place при отсутствии явного значения опираются на build.
        if ("break".equals(permKey) || "place".equals(permKey)) {
            Boolean build = perms.get("build");
            if (build != null) return build;
            return true;
        }
        // Не-базовые права (mod interactions) опираются на interact.
        if (!isBasePerm(permKey)) {
            Boolean general = perms.get("interact");
            if (general != null) return general;
        }
        return true;
    }

    /** Используется в блокирующей логике мира (защита территории). */
    public boolean playerAllowed(String playerName, String mode, String permKey) {
        boolean member = isMember(playerName);
        if (member && permKey != null) {
            String role = getRole(playerName);
            if (role != null && hasCustomRole(role) && !getRolePermission(role, permKey)) return false;
        }
        if ("ALL".equals(mode))  return true;
        if ("NONE".equals(mode)) return false;
        return member;
    }

    /**
     * Проверяет, разрешено ли действие по роли.
     * Используется для команд управления страной (пригласить/исключить/территория).
     * Лидер и заместитель — всегда могут. Кастомная роль — по своему праву.
     * Стандартная роль "Участник" — не даёт доп. прав.
     */
    public boolean roleAllows(String playerName, String permKey) {
        if (permKey == null) return true;
        if (!isMember(playerName)) return false;
        String role = getRole(playerName);
        if (role == null) return false;
        if ("Лидер".equals(role) || "Заместитель".equals(role)) return true;
        if (hasCustomRole(role)) return getRolePermission(role, permKey);
        return false;
    }

    public void deleteCustomRole(String roleName) {
        customRoles.remove(roleName);
        members.replaceAll((k, v) -> v.equals(roleName) ? "Участник" : v);
    }

    public boolean hasCustomRole(String roleName) { return customRoles.containsKey(roleName); }

    public void setCustomRolePermission(String roleName, String perm, boolean value) {
        Map<String, Boolean> perms = customRoles.get(roleName);
        if (perms != null) perms.put(perm, value);
    }

    public boolean getCustomRolePermission(String roleName, String perm) {
        Map<String, Boolean> perms = customRoles.get(roleName);
        return perms != null && perms.getOrDefault(perm, false);
    }
}