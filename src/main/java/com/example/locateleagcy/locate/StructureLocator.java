package com.example.locateleagcy.locate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenStructure;

public class StructureLocator {

    private static MapGenStructure village;
    private static MapGenStructure stronghold;
    private static MapGenStructure mineshaft;
    private static MapGenStructure temple;

    private static Method canSpawnMethod;

    private static boolean initialized = false;

    private static void init(World world) {

        if (initialized) return;

        try {

            ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();

            IChunkProvider provider = server.currentChunkProvider;

            if (provider instanceof ChunkProviderGenerate) {

                ChunkProviderGenerate gen = (ChunkProviderGenerate) provider;

                village = getField(gen, "villageGenerator");
                stronghold = getField(gen, "strongholdGenerator");
                mineshaft = getField(gen, "mineshaftGenerator");
                temple = getField(gen, "scatteredFeatureGenerator");
            }

            canSpawnMethod = MapGenStructure.class.getDeclaredMethod("canSpawnStructureAtCoords", int.class, int.class);

            canSpawnMethod.setAccessible(true);

            initialized = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static MapGenStructure getField(Object obj, String name) throws Exception {

        Field f = obj.getClass()
            .getDeclaredField(name);
        f.setAccessible(true);
        return (MapGenStructure) f.get(obj);
    }

    public static int[] locate(World world, String type, int blockX, int blockZ) {

        init(world);

        MapGenStructure gen = getGenerator(type);
        if (gen == null) return null;

        int startChunkX = blockX >> 4;
        int startChunkZ = blockZ >> 4;

        int maxRadius = 128;

        try {
            if (canSpawn(gen, startChunkX, startChunkZ)) {
                return toBlockCoords(startChunkX, startChunkZ);
            }

            for (int radius = 1; radius <= maxRadius; radius++) {

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {

                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                        int chunkX = startChunkX + dx;
                        int chunkZ = startChunkZ + dz;

                        if (canSpawn(gen, chunkX, chunkZ)) {
                            return toBlockCoords(chunkX, chunkZ);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static boolean canSpawn(MapGenStructure gen, int chunkX, int chunkZ) throws Exception {

        return (Boolean) canSpawnMethod.invoke(gen, chunkX, chunkZ);
    }

    private static MapGenStructure getGenerator(String type) {

        if (type.equalsIgnoreCase("village")) return village;
        if (type.equalsIgnoreCase("stronghold")) return stronghold;
        if (type.equalsIgnoreCase("mineshaft")) return mineshaft;
        if (type.equalsIgnoreCase("temple")) return temple;

        return null;
    }

    private static int[] toBlockCoords(int chunkX, int chunkZ) {

        return new int[] { (chunkX << 4) + 8, (chunkZ << 4) + 8 };
    }
}
