package com.bensonskiy.scountry.events;

import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/** Проверяет, что миксины защиты Create действительно применились. */
public final class CreateGuardSelfCheck {

    private static final Logger LOGGER = LogManager.getLogger("SCountry/SelfCheck");
    private static final String HANDLER_MARKER = "countryProtect$guard";

    private static final String[][] TARGETS = {
            {"com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket",
                    "настройка блоков Create"},
            {"com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket",
                    "поворот ключом через радиальное меню"},
            {"com.simibubi.create.content.contraptions.glue.SuperGlueSelectionPacket",
                    "установка супер-клея"},
            {"com.simibubi.create.content.contraptions.glue.SuperGlueRemovalPacket",
                    "удаление супер-клея"},
            {"com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket",
                    "настройка Механической руки"},
            {"com.simibubi.create.content.logistics.depot.EjectorPlacementPacket",
                    "настройка Весового эжектора"},
            {"com.simibubi.create.content.equipment.extendoGrip.ExtendoGripInteractionPacket",
                    "Extendo Grip на расстоянии"},
    };

    private CreateGuardSelfCheck() {}

    public static void run() {
        if (!ModList.get().isLoaded("create")) {
            LOGGER.info("Create не установлен — проверка миксинов пропущена.");
            return;
        }
        int ok = 0, broken = 0;
        for (String[] entry : TARGETS) {
            Class<?> target;
            try { target = Class.forName(entry[0], false, CreateGuardSelfCheck.class.getClassLoader()); }
            catch (Throwable t) {
                broken++;
                LOGGER.error("ЗАЩИТА НЕ РАБОТАЕТ: класс {} не найден. Без защиты: {}",
                        entry[0], entry[1]);
                continue;
            }
            if (hasGuard(target)) ok++;
            else {
                broken++;
                LOGGER.error("ЗАЩИТА НЕ РАБОТАЕТ: миксин не применился к {} — изменилась сигнатура handle(). Без защиты: {}",
                        entry[0], entry[1]);
            }
        }
        if (broken == 0) LOGGER.info("Защита от Create активна: {}/{} миксинов применено.", ok, ok);
        else LOGGER.error("Защита от Create частична: {}/{} применено.", ok, ok + broken);
    }

    private static boolean hasGuard(Class<?> target) {
        try {
            for (Method m : target.getDeclaredMethods())
                if (m.getName().contains(HANDLER_MARKER)) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}