package com.example.locatelegacy.locate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locatelegacy.TickHandler;

public class LocateTaskManager {

    private static final Map<String, LocateTask> TASKS = new ConcurrentHashMap<String, LocateTask>();

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<String, CacheEntry>();

    private static final long CACHE_TTL_TICKS = 20L * 30L;

    private static final int CHECKS_PER_TICK = 200;

    private static final long DELAY_TICKS_IF_BUSY = 40L;

    public static boolean startStructure(EntityPlayer player, String type) {

        String keyPlayer = player.getCommandSenderName();

        if (TASKS.containsKey(keyPlayer)) {
            player.addChatMessage(new ChatComponentText("§c你已经有一个 locate 正在运行，输入 §e/locate cancel §c取消。"));
            return false;
        }

        if (!com.example.locatelegacy.locate.StructureLocator.isStructureSupportedInWorld(player.worldObj, type)) {
            player.addChatMessage(new ChatComponentText("§c当前维度没有可搜索结构：§e" + type));
            return false;
        }

        int cx = ((int) player.posX) >> 4;
        int cz = ((int) player.posZ) >> 4;

        String cacheKey = makeCacheKey(player.worldObj, "structure", type, cx, cz);

        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            int[] r = cached.result;
            LocateMessageUtil.sendTeleportMessage(player, r[0], r[1]);
            return true;
        }

        LocateTask task = new LocateTask(player, player.worldObj, LocateTask.Mode.STRUCTURE, type, cacheKey);
        TASKS.put(keyPlayer, task);

        player.addChatMessage(new ChatComponentText("§7正在扫描最近结构...（可用 §e/locate cancel §7取消）"));

        if (isWorldBusyAroundPlayer(player)) {
            task.notBeforeTick = TickHandler.getServerTicks() + DELAY_TICKS_IF_BUSY;
            player.addChatMessage(new ChatComponentText("§e检测到区块正在生成/加载，已自动延迟 2 秒执行以避免卡顿。"));
        }

        task.generateMoreCandidatesAsync();
        return true;
    }

    public static boolean startBiome(EntityPlayer player, String biomeName) {

        String keyPlayer = player.getCommandSenderName();

        if (TASKS.containsKey(keyPlayer)) {
            player.addChatMessage(new ChatComponentText("§c你已经有一个 locate 正在运行，输入 §e/locate cancel §c取消。"));
            return false;
        }

        World world = player.worldObj;
        int dim = world.provider.dimensionId;

        BiomeGenBase target = null;
        String displayName = null;

        if (dim == 0) {
            target = com.example.locatelegacy.locate.BiomeLocator.findBiomeByNameGlobal(biomeName);
            if (target != null) displayName = target.biomeName;
        } else {
            target = com.example.locatelegacy.locate.BiomeLocator.findBiomeByNameObserved(world, player, biomeName);
            if (target != null) displayName = target.biomeName;
        }

        if (target == null || displayName == null) {
            player.addChatMessage(new ChatComponentText("§c当前维度没有该群系：§e" + biomeName));
            player.addChatMessage(new ChatComponentText("§7提示：先在本维度走动加载一些区块，再按 Tab 查看可用群系。"));
            return false;
        }
        if (dim != 0 && !com.example.locatelegacy.locate.BiomeLocator.isBiomeObserved(world, player, target)) {
            player.addChatMessage(new ChatComponentText("§c当前维度没有该群系：§e" + target.biomeName));
            return false;
        }

        int cx = ((int) player.posX) >> 4;
        int cz = ((int) player.posZ) >> 4;

        String cacheKey = makeCacheKey(world, "biome", displayName, cx, cz);

        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            int[] r = cached.result;
            player.addChatMessage(new ChatComponentText("§7(缓存命中)"));
            LocateMessageUtil.sendTeleportMessage(player, r[0], r[1]);
            return true;
        }

        LocateTask task = new LocateTask(player, world, LocateTask.Mode.BIOME, displayName, cacheKey);
        task.biomeTarget = target;

        TASKS.put(keyPlayer, task);

        player.addChatMessage(new ChatComponentText("§7正在扫描最近生物群系...（可用 §e/locate cancel §7取消）"));

        if (isWorldBusyAroundPlayer(player)) {
            task.notBeforeTick = TickHandler.getServerTicks() + DELAY_TICKS_IF_BUSY;
            player.addChatMessage(new ChatComponentText("§e检测到区块正在生成/加载，已自动延迟 2 秒执行以避免卡顿。"));
        }

        task.generateMoreCandidatesAsync();
        return true;
    }

    public static void cancel(EntityPlayer player) {

        String keyPlayer = player.getCommandSenderName();
        LocateTask t = TASKS.remove(keyPlayer);

        if (t != null) {
            t.cancelled = true;
            player.addChatMessage(new ChatComponentText("§c已取消 locate。"));
        } else {
            player.addChatMessage(new ChatComponentText("§7没有正在运行的 locate。"));
        }
    }

    public static void tick() {

        if (TASKS.isEmpty()) return;

        for (Map.Entry<String, LocateTask> entry : TASKS.entrySet()) {

            LocateTask task = entry.getValue();
            if (task.cancelled) {
                TASKS.remove(entry.getKey());
                continue;
            }

            if (TickHandler.getServerTicks() < task.notBeforeTick) {
                continue;
            }

            if (task.candidates.size() < 2000 && !task.generating) {
                task.generateMoreCandidatesAsync();
            }

            int checked = 0;

            while (checked < CHECKS_PER_TICK) {

                Long packed = task.candidates.poll();
                if (packed == null) break;

                int chunkX = (int) (packed >> 32);
                int chunkZ = (int) (packed & 0xFFFFFFFFL);

                int[] found = task.checkOne(chunkX, chunkZ);
                checked++;

                if (found != null) {
                    finishTask(entry.getKey(), task, found);
                    break;
                }
            }

            if (task.candidates.isEmpty() && task.spiralRadius > task.maxSpiralRadius && !task.generating) {
                failTask(entry.getKey(), task);
            }
        }
    }

    private static void finishTask(String playerKey, final LocateTask task, final int[] result) {

        TASKS.remove(playerKey);
        CACHE.put(task.cacheKey, new CacheEntry(result));

        TickHandler.runOnMainThread(new Runnable() {

            @Override
            public void run() {
                if (task.playerRef == null) return;
                EntityPlayer p = task.playerRef;
                LocateMessageUtil.sendTeleportMessage(p, result[0], result[1]);
            }
        });
    }

    private static void failTask(String playerKey, final LocateTask task) {

        TASKS.remove(playerKey);

        TickHandler.runOnMainThread(new Runnable() {

            @Override
            public void run() {
                if (task.playerRef == null) return;
                task.playerRef.addChatMessage(new ChatComponentText("§c未找到目标（建议扩大探索范围或稍后再试）"));
            }
        });
    }

    private static boolean isWorldBusyAroundPlayer(EntityPlayer player) {

        World w = player.worldObj;
        int cx = ((int) player.posX) >> 4;
        int cz = ((int) player.posZ) >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!w.getChunkProvider()
                    .chunkExists(cx + dx, cz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String makeCacheKey(World world, String mode, String query, int chunkX, int chunkZ) {

        int bx = chunkX >> 3;
        int bz = chunkZ >> 3;

        return world.provider.dimensionId + "|" + mode + "|" + query.toLowerCase() + "|" + bx + "|" + bz;
    }

    private static boolean isValidStructureType(String type) {
        return "village".equals(type) || "stronghold".equals(type) || "mineshaft".equals(type) || "temple".equals(type);
    }

    private static BiomeGenBase findBiomeByNameGlobal(String name) {

        if (name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

        for (BiomeGenBase biome : biomes) {
            if (biome == null || biome.biomeName == null) continue;

            if (biome.biomeName.equalsIgnoreCase(q)) return biome;
        }

        String low = q.toLowerCase();

        for (BiomeGenBase biome : biomes) {
            if (biome == null || biome.biomeName == null) continue;

            if (biome.biomeName.toLowerCase()
                .contains(low)) return biome;
        }

        return null;
    }

    private static final class CacheEntry {

        final int[] result;
        final long expireAtTick;

        CacheEntry(int[] result) {
            this.result = result;
            this.expireAtTick = TickHandler.getServerTicks() + CACHE_TTL_TICKS;
        }

        boolean isExpired() {
            return TickHandler.getServerTicks() > expireAtTick;
        }
    }
}
