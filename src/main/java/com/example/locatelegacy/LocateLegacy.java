package com.example.locatelegacy;

import java.io.File;

import com.example.locatelegacy.command.LocateCommand;
import com.example.locatelegacy.config.LocateLegacyConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = LocateLegacy.MODID, name = "Locate Legacy", version = "2.5")
public class LocateLegacy {

    public static final String MODID = "locatelegacy";

    private static boolean tickRegistered = false;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File cfg = new File(event.getModConfigurationDirectory(), "LocateLegacy.cfg");
        LocateLegacyConfig.load(cfg);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (!tickRegistered) {
            FMLCommonHandler.instance()
                .bus()
                .register(new TickHandler());
            tickRegistered = true;
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }
}
