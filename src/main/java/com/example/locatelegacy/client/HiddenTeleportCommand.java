package com.example.locatelegacy.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HiddenTeleportCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "lltp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lltp <x> <y> <z>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length != 3) return;
        try {
            int x = parseInt(sender, args[0]);
            int y = parseInt(sender, args[1]);
            int z = parseInt(sender, args[2]);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.sendChatMessage("/tp " + x + " " + y + " " + z);
            }
        } catch (Throwable ignored) {} finally {
            removeFromChatHistory();
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromChatHistory() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) return;
            List<String> sent = mc.ingameGUI.getChatGUI()
                .getSentMessages();
            if (sent == null || sent.isEmpty()) return;
            for (int i = sent.size() - 1; i >= 0; i--) {
                String s = sent.get(i);
                if (s != null && s.startsWith("/lltp ")) {
                    sent.remove(i);
                }
            }
        } catch (Throwable ignored) {}
    }
}
