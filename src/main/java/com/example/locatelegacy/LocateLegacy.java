package com.example.locatelegacy;

import com.example.locatelegacy.command.LocateCommand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = LocateLegacy.MODID, name = "Locate Legacy", version = "2.3.1")
public class LocateLegacy {

    public static final String MODID = "locatelegacy";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new TickHandler());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }
}
