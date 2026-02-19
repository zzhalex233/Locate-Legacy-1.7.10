package com.example.locateleagcy.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locateleagcy.locate.BiomeLocator;
import com.example.locateleagcy.locate.StructureLocator;

public class LocateCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "locate";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/locate <structure|biome> ...";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) sender;

        if (args.length < 2) {
            sendUsage(player);
            return;
        }

        String mode = args[0];

        // STRUCTURE
        if (mode.equalsIgnoreCase("structure")) {

            if (args.length != 2) {
                sendUsage(player);
                return;
            }

            String type = args[1];

            player.addChatMessage(new ChatComponentText("§7正在扫描最近结构..."));

            int[] result = StructureLocator.locate(player.worldObj, type, (int) player.posX, (int) player.posZ);

            if (result != null) {
                sendTeleportMessage(player, result[0], 64, result[1]);
            } else {
                player.addChatMessage(new ChatComponentText("§c未找到结构"));
            }

            return;
        }

        // BIOME
        if (mode.equalsIgnoreCase("biome")) {

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }

            String biomeName = sb.toString();

            player.addChatMessage(new ChatComponentText("§7正在扫描最近生物群系..."));

            int[] result = BiomeLocator.locate(player.worldObj, biomeName, (int) player.posX, (int) player.posZ);

            if (result != null) {
                sendTeleportMessage(player, result[0], 64, result[1]);
            } else {
                player.addChatMessage(new ChatComponentText("§c未找到该生物群系"));
            }

            return;
        }

        sendUsage(player);
    }

    private void sendUsage(EntityPlayer player) {

        player.addChatMessage(
            new ChatComponentText(
                "§e用法:\n" + "§6/locate structure <village|stronghold|mineshaft|temple>\n" + "§6/locate biome <name>"));
    }

    private void sendTeleportMessage(EntityPlayer player, int x, int y, int z) {

        ChatComponentText prefix = new ChatComponentText("§a找到坐标: ");

        ChatComponentText coords = new ChatComponentText("§e" + x + " " + y + " " + z);

        ChatStyle style = new ChatStyle();

        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + x + " " + y + " " + z));

        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("§7点击传送")));

        coords.setChatStyle(style);

        prefix.appendSibling(coords);

        player.addChatMessage(prefix);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "structure", "biome");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {

            return getListOfStringsMatchingLastWord(args, "village", "stronghold", "mineshaft", "temple");
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("biome")) {

            List<String> biomeNames = new ArrayList<String>();

            BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

            for (BiomeGenBase biome : biomes) {

                if (biome == null) continue;

                biomeNames.add(biome.biomeName);
            }

            return getListOfStringsMatchingLastWord(args, biomeNames.toArray(new String[0]));
        }

        return null;
    }
}
