package com.example.locateleagcy.locate;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 一个 locate 任务：分帧检查 + 后台生成候选点
 */
public class LocateTask {

    public enum Mode {
        STRUCTURE,
        BIOME
    }

    // 玩家引用（只在主线程用它发消息）
    public final EntityPlayer playerRef;

    public final World worldRef;

    public final Mode mode;

    public final String query;

    public final String cacheKey;

    public volatile boolean cancelled = false;

    public volatile boolean generating = false;

    // 延迟到这个 tick 才允许开始检查（用于避开区块生成风暴）
    public volatile long notBeforeTick = 0L;

    // 候选点队列（后台线程生成，主线程消费）
    public final ConcurrentLinkedQueue<Long> candidates = new ConcurrentLinkedQueue<Long>();

    // spiral 参数（只用于后台生成候选点）
    public int spiralRadius = 0;

    // 最大搜索半径（chunk 半径），可以按需调大
    public final int maxSpiralRadius;

    // biome 目标（只在 BIOME 模式使用）
    public BiomeGenBase biomeTarget;

    private final int startChunkX;
    private final int startChunkZ;

    public LocateTask(EntityPlayer player, World world, Mode mode, String query, String cacheKey) {

        this.playerRef = player;
        this.worldRef = world;
        this.mode = mode;
        this.query = query;
        this.cacheKey = cacheKey;

        this.startChunkX = ((int) player.posX) >> 4;
        this.startChunkZ = ((int) player.posZ) >> 4;

        // BIOME 建议比 STRUCTURE 更大一些，因为 biome 可能离很远
        this.maxSpiralRadius = (mode == Mode.BIOME) ? 1024 : 512;
    }

    /**
     * 后台线程生成一批候选点（纯计算，不碰 world）
     */
    public void generateMoreCandidatesAsync() {

        if (generating) return;

        generating = true;

        LocateExecutor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    // 每次生成一批“边框环”点（越多越快，但队列太大也浪费）
                    int rings = 16;

                    for (int i = 0; i < rings; i++) {

                        if (cancelled) break;

                        if (spiralRadius > maxSpiralRadius) break;

                        int r = spiralRadius;

                        // r==0 只放中心点
                        if (r == 0) {
                            offer(startChunkX, startChunkZ);
                        } else {
                            // 只生成这一圈边框：减少重复
                            for (int dx = -r; dx <= r; dx++) {
                                offer(startChunkX + dx, startChunkZ - r);
                                offer(startChunkX + dx, startChunkZ + r);
                            }
                            for (int dz = -r + 1; dz <= r - 1; dz++) {
                                offer(startChunkX - r, startChunkZ + dz);
                                offer(startChunkX + r, startChunkZ + dz);
                            }
                        }

                        // 半径递增
                        spiralRadius++;
                    }

                } finally {
                    generating = false;
                }
            }
        });
    }

    /**
     * 主线程：检查一个候选 chunk 是否命中目标
     */
    public int[] checkOne(int chunkX, int chunkZ) {

        if (cancelled) return null;

        if (mode == Mode.STRUCTURE) {

            // 不强制加载 chunk！只用结构算法判断“应当生成”的位置
            boolean ok = StructureLocator.canSpawnAt(worldRef, query, chunkX, chunkZ);
            if (ok) {
                return new int[] { (chunkX << 4) + 8, (chunkZ << 4) + 8 };
            }
            return null;
        }

        if (mode == Mode.BIOME) {

            if (biomeTarget == null) return null;

            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;

            // 获取该点群系（主线程调用，线程安全）
            BiomeGenBase biomeHere = worldRef.getBiomeGenForCoords(x, z);

            if (biomeHere == biomeTarget) {
                return new int[] { x, z };
            }
            return null;
        }

        return null;
    }

    private void offer(int chunkX, int chunkZ) {
        candidates.add(pack(chunkX, chunkZ));
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }
}
