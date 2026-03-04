package com.example.locatelegacy.command;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locatelegacy.config.BiomeListManager;
import com.example.locatelegacy.config.LearnProfileManager;
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
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.structure_id_format"));
                return;
            }

            List<String> available = getAvailableStructureIdsMerged(world);
            if (available == null || available.isEmpty() || !containsIgnoreCase(available, id)) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.unknown_structure", id));
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.structure_not_locatable", id));
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
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.scan_start"));
            List<String> raw = StructureLocator.debugListStructureGenerators(world);
            if (raw != null) {
                for (String s : raw) {
                    player.addChatMessage(colored("  " + s, EnumChatFormatting.DARK_GRAY));
                }
            }
            int cx = ((int) player.posX) >> 4;
            int cz = ((int) player.posZ) >> 4;
            List<StructureLocator.DebugStructureInfo> infos = StructureLocator.debugDescribeStructures(world, cx, cz);
            if (infos == null || infos.isEmpty()) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.no_mapgen"));
            } else {
                int i = 1;
                for (StructureLocator.DebugStructureInfo info : infos) {
                    sendStructureDebugBlock(player, i++, info);
                }
            }
            return;
        }

        if (args.length >= 2 && "learn".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.learn_usage"));
                return;
            }

            String fullId = args[2].trim()
                .toLowerCase();
            if (fullId.indexOf(':') < 0) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.structure_id_format"));
                return;
            }

            StructureLocator.DebugLearnResult learned = StructureLocator
                .debugLearnStructureAt(world, fullId, (int) Math.floor(player.posX), (int) Math.floor(player.posZ));

            if (learned == null) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.learn_no_structure"));
                return;
            }

            LearnProfileManager.LearnSummary summary = LearnProfileManager.recordLearn(learned);
            if (summary == null) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.learn_save_failed"));
                return;
            }

            sendLearnSummaryBlock(player, learned, summary);
            return;
        }

        if (args.length == 2 && "clearlearn".equalsIgnoreCase(args[1])) {
            boolean ok = LearnProfileManager.clear();
            if (ok) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.clearlearn_ok"));
            } else {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.clearlearn_failed"));
            }
            return;
        }

        if (args.length == 2 && "biome".equalsIgnoreCase(args[1])) {
            int x = (int) Math.floor(player.posX);
            int z = (int) Math.floor(player.posZ);
            BiomeGenBase b = world.getBiomeGenForCoords(x, z);
            if (b == null) {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.biome_null"));
            } else {
                player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.biome_info", b.biomeName));
            }
            return;
        }

        player.addChatMessage(new ChatComponentTranslation("locatelegacy.debug.usage"));
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
            return getListOfStringsMatchingLastWord(args, "structures", "biome", "learn", "clearlearn");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("learn")) {
            List<String> merged = getAvailableStructureIdsMerged(w);
            if (merged == null || merged.isEmpty()) return null;
            return getListOfStringsMatchingLastWord(args, merged.toArray(new String[0]));
        }

        // structure
        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {
            List<String> merged = getAvailableStructureIdsMerged(w);
            if (merged == null || merged.isEmpty()) {
                return getListOfStringsMatchingLastWord(
                    args,
                    StatCollector.translateToLocal("locatelegacy.tab.no_structure"));
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
                return getListOfStringsMatchingLastWord(
                    args,
                    StatCollector.translateToLocal("locatelegacy.tab.no_biome"));
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
        player.addChatMessage(new ChatComponentTranslation("locatelegacy.usage.debug.structures"));
        player.addChatMessage(new ChatComponentTranslation("locatelegacy.usage.debug.biome"));
    }

    private net.minecraft.util.IChatComponent buildCopyComponent(String rawJson) {
        String encoded = Base64.getEncoder()
            .encodeToString(rawJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ChatComponentText c = new ChatComponentText(StatCollector.translateToLocal("locatelegacy.debug.copy_button"));
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.AQUA);
        style.setChatClickEvent(
            new net.minecraft.event.ClickEvent(
                net.minecraft.event.ClickEvent.Action.RUN_COMMAND,
                "/llcopy " + encoded));
        style.setChatHoverEvent(
            new net.minecraft.event.HoverEvent(
                net.minecraft.event.HoverEvent.Action.SHOW_TEXT,
                new ChatComponentTranslation("locatelegacy.debug.copy_hover")));
        c.setChatStyle(style);
        return c;
    }

    private void sendStructureDebugBlock(EntityPlayer player, int index, StructureLocator.DebugStructureInfo info) {
        player.addChatMessage(colored("------------------------------", EnumChatFormatting.DARK_GRAY));
        player.addChatMessage(
            colored("[" + index + "] " + info.fullId + "  (dim " + info.dim + ")", EnumChatFormatting.GOLD));
        player.addChatMessage(colored("  mapGen: " + info.mapGenClass, EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  biome: " + info.biomeRule, EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  height: " + info.heightRule, EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  occupiedChunkDiameter: " + info.diameterRule, EnumChatFormatting.GRAY));

        EnumChatFormatting sampleColor = info.sampleStatus != null && info.sampleStatus.toUpperCase()
            .contains("FAIL") ? EnumChatFormatting.RED : EnumChatFormatting.GREEN;
        player.addChatMessage(colored("  sample: " + info.sampleStatus, sampleColor));
        player.addChatMessage(buildCopyComponent(info.copyJson));
    }

    private static ChatComponentText colored(String text, EnumChatFormatting color) {
        ChatComponentText c = new ChatComponentText(text);
        ChatStyle st = new ChatStyle();
        st.setColor(color);
        c.setChatStyle(st);
        return c;
    }

    private void sendLearnSummaryBlock(EntityPlayer player, StructureLocator.DebugLearnResult r,
        LearnProfileManager.LearnSummary s) {
        player.addChatMessage(colored("------------------------------", EnumChatFormatting.DARK_GRAY));
        player.addChatMessage(colored("[Learn] " + r.fullId + " (dim " + r.dim + ")", EnumChatFormatting.GOLD));
        player.addChatMessage(
            colored("  mapGen: " + (s.mapGen == null ? r.mapGenClass : s.mapGen), EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  providerField: " + r.providerField, EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  samples(total): " + s.samples, EnumChatFormatting.AQUA));
        player.addChatMessage(
            colored(
                "  occupiedChunkDiameter(determined): "
                    + (s.occupiedChunkDiameter != null ? s.occupiedChunkDiameter.intValue() : "UNKNOWN"),
                EnumChatFormatting.GRAY));
        player.addChatMessage(
            colored(
                "  occupiedChunkDiameter(range): "
                    + (s.occupiedChunkDiameterMin != null ? s.occupiedChunkDiameterMin.intValue() : "UNKNOWN")
                    + " .. "
                    + (s.occupiedChunkDiameterMax != null ? s.occupiedChunkDiameterMax.intValue() : "UNKNOWN"),
                EnumChatFormatting.GRAY));
        player.addChatMessage(
            colored(
                "  heightRange(merged): " + (s.heightMinY != null ? s.heightMinY.intValue() : "UNKNOWN")
                    + " .. "
                    + (s.heightMaxY != null ? s.heightMaxY.intValue() : "UNKNOWN"),
                EnumChatFormatting.GRAY));
        player.addChatMessage(
            colored(
                "  avgHeight(merged): "
                    + (s.heightAvgY != null ? String.format(java.util.Locale.ROOT, "%.2f", s.heightAvgY) : "UNKNOWN"),
                EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  biomeWhitelist(merged): " + s.biomeWhitelist, EnumChatFormatting.GRAY));
        player.addChatMessage(colored("  note: " + r.note, EnumChatFormatting.DARK_GRAY));
        player.addChatMessage(buildCopyComponent(s.copyJson));
    }
}
