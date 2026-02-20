package com.example.locateleagcy;

import com.example.locateleagcy.command.LocateCommand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = LocateLeagcy.MODID, name = "Locate Leagcy", version = "2.0")
public class LocateLeagcy {

    public static final String MODID = "locateleagcy";

    /**
     * ✅ 初始化阶段：注册 TickHandler
     * 注意：TickEvent 属于 FMLCommonHandler 的 bus（不是 MinecraftForge.EVENT_BUS）
     */
    @EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new TickHandler());
    }

    /**
     * ✅ 服务器启动阶段：注册命令
     */
    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }
}
