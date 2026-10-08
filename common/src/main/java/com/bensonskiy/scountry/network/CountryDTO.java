package com.bensonskiy.scountry.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.*;

/**
 * DTO страны. Хранит ТОЛЬКО данные, нужные клиенту.
 * Никаких ссылок на Country / CountryManager — их тут не должно быть.
 * Создание снимка из Country живёт на сервере: SCountryServer.snapshotOf.
 */
public final class CountryDTO {

    public String name, leader;
    public int color;

    public String pvpMode, buildMode, breakMode, placeMode,
            pickupMode, interactMode, explosionMode;

    public Map<String, String> members = new HashMap<>();
    public Map<String, String> modSettings = new HashMap<>();
    public Map<String, Map<String, Boolean>> customRoles = new LinkedHashMap<>();
    public Set<Long> chunkKeys = new HashSet<>();

    public CountryDTO() {}

    public void write(RegistryFriendlyByteBuf b) {
        b.writeUtf(name);
        b.writeUtf(leader);
        b.writeInt(color);
        b.writeUtf(nz(pvpMode));
        b.writeUtf(nz(buildMode));
        b.writeUtf(nz(breakMode));
        b.writeUtf(nz(placeMode));
        b.writeUtf(nz(pickupMode));
        b.writeUtf(nz(interactMode));
        b.writeUtf(nz(explosionMode));

        b.writeVarInt(members.size());
        members.forEach((k, v) -> { b.writeUtf(k); b.writeUtf(v); });

        b.writeVarInt(modSettings.size());
        modSettings.forEach((k, v) -> { b.writeUtf(k); b.writeUtf(v); });

        b.writeVarInt(customRoles.size());
        customRoles.forEach((role, perms) -> {
            b.writeUtf(role);
            b.writeVarInt(perms.size());
            perms.forEach((k, v) -> { b.writeUtf(k); b.writeBoolean(v); });
        });

        b.writeVarInt(chunkKeys.size());
        for (long k : chunkKeys) b.writeLong(k);
    }

    public static CountryDTO read(RegistryFriendlyByteBuf b) {
        CountryDTO d = new CountryDTO();
        d.name = b.readUtf();
        d.leader = b.readUtf();
        d.color = b.readInt();
        d.pvpMode = b.readUtf();
        d.buildMode = b.readUtf();
        d.breakMode = b.readUtf();
        d.placeMode = b.readUtf();
        d.pickupMode = b.readUtf();
        d.interactMode = b.readUtf();
        d.explosionMode = b.readUtf();

        int nm = b.readVarInt();
        for (int i = 0; i < nm; i++) d.members.put(b.readUtf(), b.readUtf());

        int ns = b.readVarInt();
        for (int i = 0; i < ns; i++) d.modSettings.put(b.readUtf(), b.readUtf());

        int nr = b.readVarInt();
        for (int i = 0; i < nr; i++) {
            String role = b.readUtf();
            int np = b.readVarInt();
            Map<String, Boolean> perms = new HashMap<>();
            for (int j = 0; j < np; j++) perms.put(b.readUtf(), b.readBoolean());
            d.customRoles.put(role, perms);
        }

        int nk = b.readVarInt();
        for (int i = 0; i < nk; i++) d.chunkKeys.add(b.readLong());
        return d;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}