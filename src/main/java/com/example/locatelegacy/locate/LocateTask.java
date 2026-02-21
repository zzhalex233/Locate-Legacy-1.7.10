package com.example.locatelegacy.locate;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class LocateTask {

    public enum Mode {
        STRUCTURE,
        BIOME
    }

    public final EntityPlayer playerRef;

    public final World worldRef;

    public final Mode mode;

    public final String query;

    public final String cacheKey;

    public volatile boolean cancelled = false;

    public volatile boolean generating = false;

    public volatile long notBeforeTick = 0L;

    public final ConcurrentLinkedQueue<Long> candidates = new ConcurrentLinkedQueue<Long>();

    public int spiralRadius = 0;

    public final int maxSpiralRadius;

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

        this.maxSpiralRadius = (mode == Mode.BIOME) ? 1024 : 512;
    }

    public void generateMoreCandidatesAsync() {

        if (generating) return;

        generating = true;

        LocateExecutor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    int rings = 16;

                    for (int i = 0; i < rings; i++) {

                        if (cancelled) break;

                        if (spiralRadius > maxSpiralRadius) break;

                        int r = spiralRadius;

                        if (r == 0) {
                            offer(startChunkX, startChunkZ);
                        } else {
                            for (int dx = -r; dx <= r; dx++) {
                                offer(startChunkX + dx, startChunkZ - r);
                                offer(startChunkX + dx, startChunkZ + r);
                            }
                            for (int dz = -r + 1; dz <= r - 1; dz++) {
                                offer(startChunkX - r, startChunkZ + dz);
                                offer(startChunkX + r, startChunkZ + dz);
                            }
                        }

                        spiralRadius++;
                    }

                } finally {
                    generating = false;
                }
            }
        });
    }

    public int[] checkOne(int chunkX, int chunkZ) {

        if (cancelled) return null;

        if (mode == Mode.STRUCTURE) {

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
