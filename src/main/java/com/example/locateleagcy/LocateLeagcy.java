package com.example.locateleagcy;

import com.example.locateleagcy.command.LocateCommand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = LocateLeagcy.MODID, name = "Locate Leagcy", version = "2.3SaveTeleport")
public class LocateLeagcy {

    public static final String MODID = "locateleagcy";

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
