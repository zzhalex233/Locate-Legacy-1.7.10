package com.example.locatelegacy.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locatelegacy.config.BiomeListManager;
import com.example.locatelegacy.config.StructureConfigManager;
import com.example.locatelegacy.config.StructureDefinition;
import com.example.locatelegacy.locate.LocateTaskManager;
import com.example.locatelegacy.locate.StructureLocator;

public class LocateCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "locate";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/locate <structure|biome|cancel|debug> ...";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) sender;
        World world = player.worldObj;
        int dim = world.provider.dimensionId;

        player.addChatMessage(
            new ChatComponentTranslation("locatelegacy.msg.dimension", player.getCommandSenderName(), dim));

        if (args.length < 1) {
            sendUsage(player);
            return;
        }

        String mode = args[0];

        if (mode.equalsIgnoreCase("cancel")) {
            LocateTaskManager.cancel(player);
            return;
        }

        if (mode.equalsIgnoreCase("debug")) {
            handleDebug(player, args);
            return;
        }

        if (mode.equalsIgnoreCase("structure")) {

            if (args.length != 2) {
                sendUsage(player);
                return;
            }

            String id = args[1].toLowerCase();

            if (id.indexOf(':') < 0) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.unknown_structure", id));
                player.addChatMessage(new ChatComponentText("结构ID必须是 namespace:id（例如 minecraft:village）"));
                return;
            }

            List<String> available = getAvailableStructureIdsMerged(world);
            if (available == null || available.isEmpty() || !containsIgnoreCase(available, id)) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.unknown_structure", id));
                player.addChatMessage(new ChatComponentText("当前维度没有该结构可 locate：" + id));
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

    private void handleDebug(EntityPlayer player, String[] args) {
        World world = player.worldObj;

        if (args.length == 2 && "structures".equalsIgnoreCase(args[1])) {
            player.addChatMessage(new ChatComponentText("[LocateLegacy] Debug: scanning MapGenStructure fields.."));
            List<String> lines = StructureLocator.debugListStructureGenerators(world);
            if (lines == null || lines.isEmpty()) {
                player.addChatMessage(
                    new ChatComponentText("[LocateLegacy] No MapGenStructure found in this dimension."));
            } else {
                for (String s : lines) {
                    player.addChatMessage(new ChatComponentText(s));
                }
            }
            return;
        }

        if (args.length == 2 && "biome".equalsIgnoreCase(args[1])) {
            int x = (int) Math.floor(player.posX);
            int z = (int) Math.floor(player.posZ);
            BiomeGenBase b = world.getBiomeGenForCoords(x, z);
            if (b == null) {
                player.addChatMessage(new ChatComponentText("[LocateLegacy] Debug biome: <null>"));
            } else {
                player.addChatMessage(
                    new ChatComponentText("[LocateLegacy] Debug biome: name=" + b.biomeName + " id=" + b.biomeID));
            }
            return;
        }

        player.addChatMessage(
            new ChatComponentText("[LocateLegacy] Usage: /locate debug structures | /locate debug biome"));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "structure", "biome", "cancel", "debug");
        }

        if (!(sender instanceof EntityPlayer)) return null;
        EntityPlayer p = (EntityPlayer) sender;
        World w = p.worldObj;
        int dim = w.provider.dimensionId;

        // /locate debug <...>
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return getListOfStringsMatchingLastWord(args, "structures", "biome");
        }

        // structure
        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {
            List<String> merged = getAvailableStructureIdsMerged(w);
            if (merged == null || merged.isEmpty()) {
                return getListOfStringsMatchingLastWord(args, "当前维度没有可搜索结构");
            }
            return getListOfStringsMatchingLastWord(args, merged.toArray(new String[0]));
        }

        // biome
        if (args.length >= 2 && args[0].equalsIgnoreCase("biome")) {

            List<String> merged = new ArrayList<String>();

            List<String> base;
            if (dim == 0) {
                base = com.example.locatelegacy.locate.BiomeLocator.getAllBiomeNames();
            } else {
                base = com.example.locatelegacy.locate.BiomeLocator.getObservedBiomeNames(w, p);
            }
            appendDedupIgnoreCase(merged, base);

            // config/biomepack
            List<String> extra = BiomeListManager.getBiomeNamesForDim(dim);
            appendDedupIgnoreCase(merged, extra);

            if (merged.isEmpty()) {
                return getListOfStringsMatchingLastWord(args, "当前维度暂未发现可补全群系");
            }

            return getListOfStringsMatchingLastWord(args, merged.toArray(new String[0]));
        }

        return null;
    }

    // 合并
    private static List<String> getAvailableStructureIdsMerged(World world) {
        if (world == null) return new ArrayList<String>();

        int dim = world.provider.dimensionId;

        ArrayList<String> out = new ArrayList<String>();
        Set<String> seen = new HashSet<String>(); // lower-case

        // 原来的mapGen
        List<String> base = StructureLocator.getAvailableStructureIds(world);
        if (base != null) {
            for (String s : base) {
                if (s == null) continue;
                String t = s.trim();
                if (t.length() == 0) continue;
                String k = t.toLowerCase();
                if (seen.add(k)) out.add(k);
            }
        }

        // config/biomepack
        List<StructureDefinition> defs = StructureConfigManager.getEntriesForDim(dim);
        if (defs != null) {
            for (StructureDefinition d : defs) {
                if (d == null) continue;
                String fid = d.fullId();
                if (fid == null) continue;
                String t = fid.trim();
                if (t.length() == 0) continue;
                String k = t.toLowerCase();
                if (seen.add(k)) out.add(k);
            }
        }

        return out;
    }

    private static boolean containsIgnoreCase(List<String> list, String valueLowerAlready) {
        if (list == null || valueLowerAlready == null) return false;
        String want = valueLowerAlready.trim()
            .toLowerCase();
        for (String s : list) {
            if (s != null && want.equals(
                s.trim()
                    .toLowerCase()))
                return true;
        }
        return false;
    }

    private static void appendDedupIgnoreCase(List<String> out, List<String> in) {
        if (out == null || in == null || in.isEmpty()) return;

        HashSet<String> seen = new HashSet<String>();
        for (String s : out) {
            if (s == null) continue;
            String k = s.trim()
                .toLowerCase();
            if (k.length() > 0) seen.add(k);
        }

        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (t.length() == 0) continue;
            String k = t.toLowerCase();
            if (seen.add(k)) out.add(t);
        }
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
        player.addChatMessage(new ChatComponentText("/locate debug structures"));
        player.addChatMessage(new ChatComponentText("/locate debug biome"));
    }
}
