package com.example.locatelegacy.locate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.common.BiomeDictionary;

public class StructureLocator {

    private static volatile Method canSpawnMethod;

    private static final Map<String, MapGenStructure> GEN_CACHE = new ConcurrentHashMap<String, MapGenStructure>();

    public static boolean canSpawnAt(World world, String structureId, int chunkX, int chunkZ) {

        MapGenStructure gen = getGenerator(world, structureId);
        if (gen == null) return false;

        ensureCanSpawnMethod();

        try {
            Object r = canSpawnMethod.invoke(gen, chunkX, chunkZ);
            boolean predicted = Boolean.TRUE.equals(r);
            if (!predicted) return false;

            // 过滤temple
            if (gen instanceof MapGenScatteredFeature) {
                return scatteredTypeMatch(world, structureId, chunkX, chunkZ);
            }

            return true;

        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private static boolean scatteredTypeMatch(World world, String structureId, int chunkX, int chunkZ) {

        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;

        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if (biome == null) return false;

        boolean isDesert = BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.DESERT);
        boolean isJungle = BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.JUNGLE);
        boolean isSwamp = BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP);

        if ("minecraft:desert_pyramid".equals(structureId)) return isDesert;
        if ("minecraft:jungle_pyramid".equals(structureId)) return isJungle;
        if ("minecraft:swamp_hut".equals(structureId)) return isSwamp;

        return false;
    }

    private static MapGenStructure getGenerator(World world, String structureId) {

        String key = world.provider.dimensionId + "|" + structureId.toLowerCase();
        MapGenStructure cached = GEN_CACHE.get(key);
        if (cached != null) return cached;

        MapGenStructure found = findGenerator(world, structureId);
        if (found != null) GEN_CACHE.put(key, found);

        return found;
    }

    public static MapGenStructure findGenerator(World world, String structureId) {

        try {
            if (world == null) return null;
            if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;

            ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();
            IChunkProvider provider = server.currentChunkProvider;

            String want = structureId.toLowerCase();

            for (Field field : provider.getClass()
                .getDeclaredFields()) {

                field.setAccessible(true);
                Object obj = field.get(provider);

                if (!(obj instanceof MapGenStructure)) continue;

                if ("minecraft:village".equals(want) && obj instanceof MapGenVillage) return (MapGenStructure) obj;
                if ("minecraft:stronghold".equals(want) && obj instanceof MapGenStronghold)
                    return (MapGenStructure) obj;
                if ("minecraft:mineshaft".equals(want) && obj instanceof MapGenMineshaft) return (MapGenStructure) obj;

                if (("minecraft:desert_pyramid".equals(want) || "minecraft:jungle_pyramid".equals(want)
                    || "minecraft:swamp_hut".equals(want)) && obj instanceof MapGenScatteredFeature) {
                    return (MapGenStructure) obj;
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return null;
    }

    private static void ensureCanSpawnMethod() {

        if (canSpawnMethod != null) return;

        try {
            try {
                Method m = MapGenStructure.class.getDeclaredMethod("canSpawnStructureAtCoords", int.class, int.class);
                m.setAccessible(true);
                canSpawnMethod = m;
                return;
            } catch (NoSuchMethodException ignored) {}

            Method[] ms = MapGenStructure.class.getDeclaredMethods();
            for (Method m : ms) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == int.class && p[1] == int.class && m.getReturnType() == boolean.class) {
                    m.setAccessible(true);
                    canSpawnMethod = m;
                    return;
                }
            }

            System.out.println("[LocateLegacy] ERROR: cannot resolve canSpawnStructureAtCoords method.");

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isStructureSupportedInWorld(World world, String structureId) {
        return findGenerator(world, structureId) != null;
    }
}
