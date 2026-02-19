package com.example.locateleagcy;

import com.example.locateleagcy.command.LocateCommand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = LocateLeagcy.MODID, name = "Locate Leagcy", version = "1.0.0")
public class LocateLeagcy {

    public static final String MODID = "locateleagcy";

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {

        event.registerServerCommand(new LocateCommand());
    }

    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus();
    }

    public void serverLoad(FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }

}
