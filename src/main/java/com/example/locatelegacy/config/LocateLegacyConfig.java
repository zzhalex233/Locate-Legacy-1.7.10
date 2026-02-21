package com.example.locatelegacy.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class LocateLegacyConfig {

    private LocateLegacyConfig() {}

    public static boolean clickToTeleport = true;

    private static Configuration config;

    public static void load(File cfgFile) {

        config = new Configuration(cfgFile);
        config.load();

        clickToTeleport = config.getBoolean(
            "clickToTeleport",
            Configuration.CATEGORY_GENERAL,
            true,
            "If true, click coordinate message will run /tp. If false, coordinates are not clickable.");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
