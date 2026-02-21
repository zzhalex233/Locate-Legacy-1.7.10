package com.example.locatelegacy.locate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;

public class StructureLocator {

    private static volatile Method canSpawnMethod;

    private static final Map<String, MapGenStructure> GEN_CACHE = new ConcurrentHashMap<String, MapGenStructure>();

    public static boolean canSpawnAt(World world, String type, int chunkX, int chunkZ) {

        MapGenStructure gen = getGenerator(world, type);
        if (gen == null) return false;

        ensureCanSpawnMethod();

        try {
            Object r = canSpawnMethod.invoke(gen, chunkX, chunkZ);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private static MapGenStructure getGenerator(World world, String type) {

        String key = world.provider.dimensionId + "|" + type.toLowerCase();
        MapGenStructure cached = GEN_CACHE.get(key);
        if (cached != null) return cached;

        MapGenStructure found = findGenerator(world, type);
        if (found != null) GEN_CACHE.put(key, found);

        return found;
    }

    public static MapGenStructure findGenerator(World world, String type) {

        try {
            if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;

            ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();
            IChunkProvider provider = server.currentChunkProvider;

            String want = type.toLowerCase();

            for (Field field : provider.getClass()
                .getDeclaredFields()) {

                field.setAccessible(true);
                Object obj = field.get(provider);

                if (!(obj instanceof MapGenStructure)) continue;

                if ("village".equals(want) && obj instanceof MapGenVillage) return (MapGenStructure) obj;
                if ("stronghold".equals(want) && obj instanceof MapGenStronghold) return (MapGenStructure) obj;
                if ("mineshaft".equals(want) && obj instanceof MapGenMineshaft) return (MapGenStructure) obj;
                if ("temple".equals(want) && obj instanceof MapGenScatteredFeature) return (MapGenStructure) obj;
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

    public static boolean isStructureSupportedInWorld(World world, String type) {
        return findGenerator(world, type) != null;
    }
}
