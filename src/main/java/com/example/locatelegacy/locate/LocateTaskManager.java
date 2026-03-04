package com.example.locatelegacy.locate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locatelegacy.TickHandler;

public class LocateTaskManager {

    private static final Map<String, LocateTask> TASKS = new ConcurrentHashMap<String, LocateTask>();

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<String, CacheEntry>();

    private static final long CACHE_TTL_TICKS = 20L * 30L;
    private static final long CACHE_CLEANUP_INTERVAL_TICKS = 20L * 10L;

    private static final int CHECKS_PER_TICK = 200;

    private static final long DELAY_TICKS_IF_BUSY = 40L;
    private static volatile long lastCacheCleanupTick = 0L;

    public static boolean startStructure(EntityPlayer player, String type) {

        String keyPlayer = playerKey(player);

        if (!com.example.locatelegacy.locate.StructureLocator.isStructureSupportedInWorld(player.worldObj, type)) {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.no_structures_in_dimension", type));
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
        LocateTask old = TASKS.putIfAbsent(keyPlayer, task);
        if (old != null) {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.task_running"));
            return false;
        }

        player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.scanning_structure"));

        if (isWorldBusyAroundPlayer(player)) {
            task.notBeforeTick = TickHandler.getServerTicks() + DELAY_TICKS_IF_BUSY;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.delayed_busy"));
        }

        task.generateMoreCandidatesAsync();
        return true;
    }

    public static boolean startBiome(EntityPlayer player, String biomeName) {

        String keyPlayer = playerKey(player);

        World world = player.worldObj;
        int dim = world.provider.dimensionId;

        BiomeGenBase target = null;
        String displayName = null;

        if (dim == 0) {
            target = com.example.locatelegacy.locate.BiomeLocator.findBiomeByNameGlobal(biomeName);
            if (target != null) displayName = target.biomeName;
        } else {
            List<String> observed = com.example.locatelegacy.locate.BiomeLocator.getObservedBiomeNames(world, player);
            target = findBiomeByNameFromObservedList(observed, biomeName);
            if (target != null) displayName = target.biomeName;
        }

        if (target == null || displayName == null) {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.no_biome_in_dimension", biomeName));
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.biome_hint"));
            return false;
        }
        int cx = ((int) player.posX) >> 4;
        int cz = ((int) player.posZ) >> 4;

        String cacheKey = makeCacheKey(world, "biome", displayName, cx, cz);

        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            int[] r = cached.result;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.cache_hit"));
            LocateMessageUtil.sendTeleportMessage(player, r[0], r[1]);
            return true;
        }

        LocateTask task = new LocateTask(player, world, LocateTask.Mode.BIOME, displayName, cacheKey);
        task.biomeTarget = target;

        LocateTask old = TASKS.putIfAbsent(keyPlayer, task);
        if (old != null) {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.task_running"));
            return false;
        }

        player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.scanning_biome"));

        if (isWorldBusyAroundPlayer(player)) {
            task.notBeforeTick = TickHandler.getServerTicks() + DELAY_TICKS_IF_BUSY;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.delayed_busy"));
        }

        task.generateMoreCandidatesAsync();
        return true;
    }

    public static void cancel(EntityPlayer player) {

        String keyPlayer = playerKey(player);
        LocateTask t = TASKS.remove(keyPlayer);

        if (t != null) {
            t.cancelled = true;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.cancelled"));
        } else {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.no_task"));
        }
    }

    public static void tick() {

        cleanupExpiredCacheIfNeeded();
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

            if (task.candidates.size() < 2000 && !task.isGenerating()) {
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

            if (task.candidates.isEmpty() && task.spiralRadius > task.maxSpiralRadius && !task.isGenerating()) {
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
                task.playerRef.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.not_found"));
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

        return world.provider.dimensionId + "|" + mode + "|" + query.toLowerCase(Locale.ROOT) + "|" + bx + "|" + bz;
    }

    private static String playerKey(EntityPlayer player) {
        if (player == null) return "";
        try {
            if (player.getUniqueID() != null) return player.getUniqueID()
                .toString();
        } catch (Throwable ignored) {}
        return player.getCommandSenderName();
    }

    private static BiomeGenBase findBiomeByNameFromObservedList(List<String> observed, String name) {
        if (observed == null || observed.isEmpty() || name == null) return null;

        String q = name.trim();
        if (q.length() == 0) return null;

        for (String n : observed) {
            if (n != null && n.equalsIgnoreCase(q)) {
                return com.example.locatelegacy.locate.BiomeLocator.findBiomeByNameGlobal(n);
            }
        }

        String low = q.toLowerCase();
        for (String n : observed) {
            if (n != null && n.toLowerCase()
                .contains(low)) {
                return com.example.locatelegacy.locate.BiomeLocator.findBiomeByNameGlobal(n);
            }
        }

        return null;
    }

    private static void cleanupExpiredCacheIfNeeded() {
        long now = TickHandler.getServerTicks();
        if (now - lastCacheCleanupTick < CACHE_CLEANUP_INTERVAL_TICKS) return;

        lastCacheCleanupTick = now;
        for (Map.Entry<String, CacheEntry> entry : CACHE.entrySet()) {
            CacheEntry ce = entry.getValue();
            if (ce == null || ce.isExpired(now)) {
                CACHE.remove(entry.getKey());
            }
        }
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

        boolean isExpired(long nowTick) {
            return nowTick > expireAtTick;
        }
    }
}
