package com.bensonskiy.scountry;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side configuration for SCountry protection hooks. */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue protectRightClickBlock;
    public static final ModConfigSpec.BooleanValue protectUseItem;
    public static final ModConfigSpec.BooleanValue protectLeftClickBlock;
    public static final ModConfigSpec.BooleanValue protectBlockBreak;
    public static final ModConfigSpec.BooleanValue protectAttackEntity;
    public static final ModConfigSpec.BooleanValue protectEntityInteract;
    public static final ModConfigSpec.BooleanValue protectItemPickup;
    public static final ModConfigSpec.BooleanValue protectExplosion;
    public static final ModConfigSpec.ConfigValue<String> messageDenied;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("protection");
        protectRightClickBlock = b.define("protectRightClickBlock", true);
        protectUseItem = b.define("protectUseItem", true);
        protectLeftClickBlock = b.define("protectLeftClickBlock", true);
        protectBlockBreak = b.define("protectBlockBreak", true);
        protectAttackEntity = b.define("protectAttackEntity", true);
        protectEntityInteract = b.define("protectEntityInteract", true);
        protectItemPickup = b.define("protectItemPickup", true);
        protectExplosion = b.define("protectExplosion", true);
        b.pop();
        messageDenied = b.define("messageDenied", "Действие запрещено территорией страны.");
        SPEC = b.build();
    }

    private Config() {}
}
