package com.example.locatelegacy;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import com.example.locatelegacy.command.LocateCommand;
import com.example.locatelegacy.config.BiomeListManager;
import com.example.locatelegacy.config.BiomeWalkTracker;
import com.example.locatelegacy.config.LocateLegacyConfig;
import com.example.locatelegacy.config.StructureConfigManager;
import com.example.locatelegacy.locate.LocateTaskManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = LocateLegacy.MODID,
    name = LocateLegacy.NAME,
    version = LocateLegacy.VERSION,
    acceptableRemoteVersions = "*")
public class LocateLegacy {

    public static final String MODID = "locatelegacy";
    public static final String NAME = "LocateLegacy";
    public static final String VERSION = "3.0";

    private static boolean registered = false;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File cfg = new File(event.getModConfigurationDirectory(), "LocateLegacy.cfg");
        LocateLegacyConfig.load(cfg);

        StructureConfigManager.init(event.getModConfigurationDirectory());
        BiomeListManager.init(event.getModConfigurationDirectory());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (!registered) {
            FMLCommonHandler.instance()
                .bus()
                .register(new ServerTickDriver());
            FMLCommonHandler.instance()
                .bus()
                .register(new TickHandler());
            FMLCommonHandler.instance()
                .bus()
                .register(new BiomeWalkTracker());

            registered = true;
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }

    public static class ServerTickDriver {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onServerTick(cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent e) {
            if (e.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;

            LocateTaskManager.tick();
            BiomeListManager.tickSave();
        }
    }
}
