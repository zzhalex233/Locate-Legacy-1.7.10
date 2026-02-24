package com.example.locatelegacy.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.example.locatelegacy.locate.LocateTaskManager;

public class LocateCommand extends CommandBase {

    private static final String[] STRUCTURE_IDS = new String[] { "minecraft:village", "minecraft:stronghold",
        "minecraft:mineshaft", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:swamp_hut" };

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
            new ChatComponentTranslation(
                "locatelegacy.msg.dimension",
                player.getCommandSenderName(),
                world.provider.dimensionId));

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

            String id = args[1].toLowerCase();

            if (!isValidStructureId(id)) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.unknown_structure", id));
                sendUsage(player);
                return;
            }

            LocateTaskManager.startStructure(player, id);
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

    private static boolean isValidStructureId(String id) {
        for (String s : STRUCTURE_IDS) {
            if (s.equals(id)) return true;
        }
        return false;
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
        player.addChatMessage(new ChatComponentTranslation("locatelegacy.usage.title"));
        player.addChatMessage(new ChatComponentTranslation("locatelegacy.usage.structure"));
        player.addChatMessage(new ChatComponentTranslation("locatelegacy.usage.biome"));
        player.addChatMessage(new ChatComponentTranslation("locatelegacy.usage.cancel"));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "structure", "biome", "cancel");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {

            if (sender instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) sender;

                boolean any = false;
                for (String id : STRUCTURE_IDS) {
                    if (com.example.locatelegacy.locate.StructureLocator.isStructureSupportedInWorld(p.worldObj, id)) {
                        any = true;
                        break;
                    }
                }

                if (!any) {
                    return getListOfStringsMatchingLastWord(args, "当前维度没有可搜索结构");
                }
            }

            return getListOfStringsMatchingLastWord(args, STRUCTURE_IDS);
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
