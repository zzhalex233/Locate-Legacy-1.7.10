package com.example.locatelegacy.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CopyToClipboardCommand extends CommandBase {

    private static final int MAX_BASE64_ARG_LEN = 32768;

    @Override
    public String getCommandName() {
        return "llcopy";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/llcopy <base64>";
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
        if (args == null || args.length < 1) return;
        String payload = args[0];
        if (payload == null || payload.length() == 0 || payload.length() > MAX_BASE64_ARG_LEN) {
            removeFromChatHistory();
            sender.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.copy_failed"));
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder()
                .decode(payload);
            String text = new String(decoded, StandardCharsets.UTF_8);
            GuiScreen.setClipboardString(text);
            removeFromChatHistory();
            sender.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.copied"));
        } catch (Throwable t) {
            removeFromChatHistory();
            sender.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.copy_failed"));
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
                if (s != null && s.startsWith("/llcopy ")) {
                    sent.remove(i);
                }
            }
        } catch (Throwable ignored) {}
    }
}
