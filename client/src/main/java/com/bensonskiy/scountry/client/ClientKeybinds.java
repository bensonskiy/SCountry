package com.bensonskiy.scountry.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeybinds {

    public static final String CATEGORY = "key.categories.scountry";

    /** По умолчанию — "]". В русской раскладке на этой клавише — "ъ". */
    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.scountry.open_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }
}