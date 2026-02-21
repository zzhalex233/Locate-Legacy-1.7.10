package com.example.locatelegacy.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.example.locatelegacy.locate.LocateTaskManager;

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
        World world = player.worldObj;

        player.addChatMessage(
            new ChatComponentText("§7" + player.getCommandSenderName() + "所在的维度：§e" + world.provider.dimensionId));

        if (args.length < 1) {
            sendUsage(player);
            return;
        }

        String mode = args[0];

        if (mode.equalsIgnoreCase("cancel")) {
            LocateTaskManager.cancel(player);
            return;
        }

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

        player.addChatMessage(new ChatComponentText("§e用法:"));
        player.addChatMessage(new ChatComponentText("§6/locate structure <village|stronghold|mineshaft|temple>"));
        player.addChatMessage(new ChatComponentText("§6/locate biome <name>"));
        player.addChatMessage(new ChatComponentText("§6/locate cancel"));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "structure", "biome", "cancel");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {

            if (sender instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) sender;

                boolean any = com.example.locatelegacy.locate.StructureLocator
                    .isStructureSupportedInWorld(p.worldObj, "village")
                    || com.example.locatelegacy.locate.StructureLocator
                        .isStructureSupportedInWorld(p.worldObj, "stronghold")
                    || com.example.locatelegacy.locate.StructureLocator
                        .isStructureSupportedInWorld(p.worldObj, "mineshaft")
                    || com.example.locatelegacy.locate.StructureLocator
                        .isStructureSupportedInWorld(p.worldObj, "temple");

                if (!any) {
                    return getListOfStringsMatchingLastWord(args, "当前维度没有可搜索结构");
                }
            }

            return getListOfStringsMatchingLastWord(args, "village", "stronghold", "mineshaft", "temple");
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("biome")) {

            if (sender instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) sender;
                int dim = p.worldObj.provider.dimensionId;

                List<String> names;

                if (dim == 0) {
                    names = com.example.locatelegacy.locate.BiomeLocator.getAllBiomeNames();
                } else {
                    names = com.example.locatelegacy.locate.BiomeLocator.getObservedBiomeNames(p.worldObj, p);
                }

                if (names == null || names.isEmpty()) {
                    return getListOfStringsMatchingLastWord(args, "当前维度暂未发现可补全群系");
                }

                return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
            }

            return null;
        }

        return null;
    }
}
