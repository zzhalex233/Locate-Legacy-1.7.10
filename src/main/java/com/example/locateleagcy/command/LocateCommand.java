package com.example.locateleagcy.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locateleagcy.locate.LocateTaskManager;

public class LocateCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "locate";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/locate <structure|biome|cancel> ...";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) sender;

        if (args.length < 1) {
            sendUsage(player);
            return;
        }

        String mode = args[0];

        // /locate cancel
        if (mode.equalsIgnoreCase("cancel")) {
            LocateTaskManager.cancel(player);
            return;
        }

        // /locate structure <type>
        if (mode.equalsIgnoreCase("structure")) {

            if (args.length != 2) {
                sendUsage(player);
                return;
            }

            String type = args[1].toLowerCase();

            if (!isValidStructureType(type)) {
                player.addChatMessage(new ChatComponentText("§c未知结构类型: §e" + type));
                sendUsage(player);
                return;
            }

            LocateTaskManager.startStructure(player, type);
            return;
        }

        // /locate biome <name...>
        if (mode.equalsIgnoreCase("biome")) {

            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            String biomeName = joinArgs(args, 1);

            LocateTaskManager.startBiome(player, biomeName);
            return;
        }

        sendUsage(player);
    }

    private static boolean isValidStructureType(String type) {
        return "village".equals(type) || "stronghold".equals(type) || "mineshaft".equals(type) || "temple".equals(type);
    }

    private static String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void sendUsage(EntityPlayer player) {
        player.addChatMessage(
            new ChatComponentText(
                "§e用法:\n" + "§6/locate structure <village|stronghold|mineshaft|temple>\n"
                    + "§6/locate biome <name>\n"
                    + "§6/locate cancel"));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "structure", "biome", "cancel");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {
            return getListOfStringsMatchingLastWord(args, "village", "stronghold", "mineshaft", "temple");
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("biome")) {

            // 只在补全时动态读一次列表（数量不大）
            List<String> biomeNames = new ArrayList<String>();
            BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

            for (BiomeGenBase b : biomes) {
                if (b == null) continue;
                biomeNames.add(b.biomeName);
            }

            return getListOfStringsMatchingLastWord(args, biomeNames.toArray(new String[0]));
        }

        return null;
    }
}
