package com.example.locateleagcy.locate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenStructure;

public class StructureLocator {

    private static Method canSpawnMethod;

    static {
        try {
            canSpawnMethod = MapGenStructure.class.getDeclaredMethod("canSpawnStructureAtCoords", int.class, int.class);
            canSpawnMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int[] locate(World world, String type, int blockX, int blockZ) {

        MapGenStructure gen = findGenerator(world, type);

        if (gen == null) return null;

        int startChunkX = blockX >> 4;
        int startChunkZ = blockZ >> 4;

        int maxRadius = 256;

        try {

            for (int radius = 0; radius <= maxRadius; radius++) {

                for (int dx = -radius; dx <= radius; dx++) {

                    for (int dz = -radius; dz <= radius; dz++) {

                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                        int chunkX = startChunkX + dx;
                        int chunkZ = startChunkZ + dz;

                        if (canSpawn(gen, chunkX, chunkZ)) {

                            return new int[] { (chunkX << 4) + 8, (chunkZ << 4) + 8 };
                        }
                    }
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return null;
    }

    private static boolean canSpawn(MapGenStructure gen, int chunkX, int chunkZ) throws Exception {

        return (Boolean) canSpawnMethod.invoke(gen, chunkX, chunkZ);
    }

    private static MapGenStructure findGenerator(World world, String type) {

        try {

            ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();

            IChunkProvider provider = server.currentChunkProvider;

            for (Field field : provider.getClass()
                .getDeclaredFields()) {

                field.setAccessible(true);

                Object obj = field.get(provider);

                if (obj instanceof MapGenStructure) {

                    MapGenStructure gen = (MapGenStructure) obj;

                    String name = gen.getClass()
                        .getSimpleName()
                        .toLowerCase();

                    if (name.contains(type.toLowerCase())) {

                        return gen;
                    }
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return null;
    }
}
