package com.example.locateleagcy.locate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.example.locateleagcy.TickHandler;

public class LocateTaskManager {

    private static final Map<String, LocateTask> TASKS = new ConcurrentHashMap<String, LocateTask>();

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<String, CacheEntry>();

    private static final long CACHE_TTL_TICKS = 20L * 30L; // 30s

    private static final int CHECKS_PER_TICK = 200;

    private static final long DELAY_TICKS_IF_BUSY = 40L;

    public static boolean startStructure(EntityPlayer player, String type) {

        String keyPlayer = player.getCommandSenderName();

        if (TASKS.containsKey(keyPlayer)) {
            player.addChatMessage(new ChatComponentText("§c你已经有一个 locate 正在运行，输入 §e/locate cancel §c取消。"));
            return false;
        }

        // 缓存命中：秒回
        int cx = ((int) player.posX) >> 4;
        int cz = ((int) player.posZ) >> 4;
        String cacheKey = makeCacheKey(player.worldObj, "structure", type, cx, cz);

        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            int[] r = cached.result;
            player.addChatMessage(new ChatComponentText("§7(缓存命中)"));
            LocateMessageUtil.sendTeleportMessage(player, r[0], 64, r[1]);
            return true;
        }

        LocateTask task = new LocateTask(player, player.worldObj, LocateTask.Mode.STRUCTURE, type, cacheKey);
        TASKS.put(keyPlayer, task);

        player.addChatMessage(new ChatComponentText("§7正在扫描最近结构...（可用 §e/locate cancel §7取消）"));

        // 神必提示
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

        BiomeMatch match = BiomeLocator.resolveBiome(biomeName);
        if (match == null) {
            player.addChatMessage(new ChatComponentText("§c未知群系: §e" + biomeName));
            return false;
        }

        int cx = ((int) player.posX) >> 4;
        int cz = ((int) player.posZ) >> 4;
        String cacheKey = makeCacheKey(player.worldObj, "biome", match.displayName, cx, cz);

        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            int[] r = cached.result;
            player.addChatMessage(new ChatComponentText("§7(缓存命中)"));
            LocateMessageUtil.sendTeleportMessage(player, r[0], 64, r[1]);
            return true;
        }

        LocateTask task = new LocateTask(player, player.worldObj, LocateTask.Mode.BIOME, match.displayName, cacheKey);
        task.biomeTarget = match.biome;

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

            // 延迟启动
            if (TickHandler.getServerTicks() < task.notBeforeTick) {
                continue;
            }

            // 如果候选点太少，异步再补一批
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

            // 若候选已耗尽且已经生成到较大半径仍找不到：结束
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

                LocateMessageUtil.sendTeleportMessage(p, result[0], 64, result[1]);
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

    public static final class BiomeMatch {

        public final net.minecraft.world.biome.BiomeGenBase biome;
        public final String displayName;

        public BiomeMatch(net.minecraft.world.biome.BiomeGenBase biome, String displayName) {
            this.biome = biome;
            this.displayName = displayName;
        }
    }
}
