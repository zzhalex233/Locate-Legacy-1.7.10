package com.example.locatelegacy.locate;

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

    private static final int CHECKS_PER_TICK = 200;

    private static final long DELAY_TICKS_IF_BUSY = 40L;

    public static boolean startStructure(EntityPlayer player, String type) {

        String keyPlayer = player.getCommandSenderName();

        if (TASKS.containsKey(keyPlayer)) {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.task_running"));
            return false;
        }

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
        TASKS.put(keyPlayer, task);

        player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.scanning_structure"));

        if (isWorldBusyAroundPlayer(player)) {
            task.notBeforeTick = TickHandler.getServerTicks() + DELAY_TICKS_IF_BUSY;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.delayed_busy"));
        }

        task.generateMoreCandidatesAsync();
        return true;
    }

    public static boolean startBiome(EntityPlayer player, String biomeName) {

        String keyPlayer = player.getCommandSenderName();

        if (TASKS.containsKey(keyPlayer)) {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.task_running"));
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
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.no_biome_in_dimension", biomeName));
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.biome_hint"));
            return false;
        }
        if (dim != 0 && !com.example.locatelegacy.locate.BiomeLocator.isBiomeObserved(world, player, target)) {
            player.addChatMessage(
                new ChatComponentTranslation("locatelegacy.msg.no_biome_in_dimension", target.biomeName));
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

        TASKS.put(keyPlayer, task);

        player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.scanning_biome"));

        if (isWorldBusyAroundPlayer(player)) {
            task.notBeforeTick = TickHandler.getServerTicks() + DELAY_TICKS_IF_BUSY;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.delayed_busy"));
        }

        task.generateMoreCandidatesAsync();
        return true;
    }

    public static void cancel(EntityPlayer player) {

        String keyPlayer = player.getCommandSenderName();
        LocateTask t = TASKS.remove(keyPlayer);

        if (t != null) {
            t.cancelled = true;
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.cancelled"));
        } else {
            player.addChatMessage(new ChatComponentTranslation("locatelegacy.msg.no_task"));
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

        return world.provider.dimensionId + "|" + mode + "|" + query.toLowerCase() + "|" + bx + "|" + bz;
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
