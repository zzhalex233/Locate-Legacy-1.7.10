package com.example.locatelegacy;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import com.example.locatelegacy.command.LocateCommand;
import com.example.locatelegacy.config.BiomeListManager;
import com.example.locatelegacy.config.BiomeWalkTracker;
import com.example.locatelegacy.config.LearnProfileManager;
import com.example.locatelegacy.config.LocateLegacyConfig;
import com.example.locatelegacy.config.StructureConfigManager;
import com.example.locatelegacy.util.LogUtil;

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
    public static final String VERSION = "3.1";

    private static boolean registered = false;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File cfg = new File(event.getModConfigurationDirectory(), "LocateLegacy.cfg");
        LocateLegacyConfig.load(cfg);

        StructureConfigManager.init(event.getModConfigurationDirectory());
        BiomeListManager.init(event.getModConfigurationDirectory());
        LearnProfileManager.init(event.getModConfigurationDirectory());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (!registered) {
            FMLCommonHandler.instance()
                .bus()
                .register(new TickHandler());
            FMLCommonHandler.instance()
                .bus()
                .register(new BiomeWalkTracker());

            if (FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
                try {
                    Class<?> c = Class.forName("com.example.locatelegacy.client.ClientInit");
                    c.getMethod("registerClientCommands")
                        .invoke(null);
                } catch (Throwable t) {
                    LogUtil.error("Failed to register client commands.", t);
                }
            }

            registered = true;
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }
}
