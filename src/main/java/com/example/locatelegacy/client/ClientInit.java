package com.example.locatelegacy.client;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientInit {

    private static boolean inited = false;

    private ClientInit() {}

    public static void registerClientCommands() {
        if (inited) return;
        inited = true;
        ClientCommandHandler.instance.registerCommand(new CopyToClipboardCommand());
        ClientCommandHandler.instance.registerCommand(new HiddenTeleportCommand());
    }
}
