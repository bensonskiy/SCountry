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