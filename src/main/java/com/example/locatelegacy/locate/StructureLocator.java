package com.example.locatelegacy.locate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenNetherBridge;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.common.BiomeDictionary;

import com.example.locatelegacy.config.StructureConfigManager;
import com.example.locatelegacy.config.StructureDefinition;


public class StructureLocator {

    private static volatile Method canSpawnMethod;

    private static final Map<String, MapGenStructure> GEN_CACHE = new ConcurrentHashMap<String, MapGenStructure>();

    private static final String[] VANILLA_IDS = new String[] { "minecraft:village", "minecraft:stronghold",
        "minecraft:mineshaft", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:swamp_hut",
        "minecraft:fortress" };

    public static boolean canSpawnAt(World world, String structureId, int chunkX, int chunkZ) {
        if (world == null || structureId == null) return false;

        MapGenStructure gen = getGenerator(world, structureId);
        if (gen == null) return false;

        ensureCanSpawnMethod();

        try {
            Object r = canSpawnMethod.invoke(gen, chunkX, chunkZ);
            boolean predicted = Boolean.TRUE.equals(r);
            if (!predicted) return false;

            // temple 用 biome
            if (gen instanceof MapGenScatteredFeature) {
                return scatteredTypeMatch(world, structureId.toLowerCase(), chunkX, chunkZ);
            }

            // mod：同一个 MapGen 可能用于生成多种结构（比如沟槽的暮色森林），过滤
            StructureDefinition def = StructureConfigManager.get(structureId.toLowerCase());
            if (def != null) {
                int x = (chunkX << 4) + 8;
                int z = (chunkZ << 4) + 8;
                BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
                if (biome == null) return false;

                int bid = biome.biomeID;
                if (!biomeMatch(def, bid)) return false;
            }

            return true;

        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private static boolean scatteredTypeMatch(World world, String structureIdLower, int chunkX, int chunkZ) {

        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;

        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if (biome == null) return false;

        boolean isDesert = BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.DESERT);
        boolean isJungle = BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.JUNGLE);
        boolean isSwamp = BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP);

        if ("minecraft:desert_pyramid".equals(structureIdLower)) return isDesert;
        if ("minecraft:jungle_pyramid".equals(structureIdLower)) return isJungle;
        if ("minecraft:swamp_hut".equals(structureIdLower)) return isSwamp;

        return false;
    }

    private static boolean biomeMatch(StructureDefinition def, int biomeId) {
        if (def == null) return true;

        if (def.biomeIdWhitelist != null && !def.biomeIdWhitelist.isEmpty()) {
            boolean ok = false;
            for (Integer w : def.biomeIdWhitelist) {
                if (w != null && w.intValue() == biomeId) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }

        // all：允许全部。搭配 blacklist,
        // 如果既没写 whitelist 也没写 all，则默认all

        // blacklist：最后排除
        if (def.biomeIdBlacklist != null && !def.biomeIdBlacklist.isEmpty()) {
            for (Integer b : def.biomeIdBlacklist) {
                if (b != null && b.intValue() == biomeId) return false;
            }
        }

        return true;
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
            if (world == null || structureId == null) return null;
            if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;

            ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();
            IChunkProvider provider = server.currentChunkProvider;
            if (provider == null) return null;

            String want = structureId.toLowerCase();

            StructureDefinition def = StructureConfigManager.get(want);
            String wantMapGen = null;
            if (def != null && def.mapGen != null
                && def.mapGen.trim()
                    .length() > 0
                && def.dim == world.provider.dimensionId) {
                wantMapGen = def.mapGen.trim();
            }

            for (Field field : provider.getClass()
                .getDeclaredFields()) {
                field.setAccessible(true);
                Object obj = field.get(provider);
                if (!(obj instanceof MapGenStructure)) continue;

                if (wantMapGen != null && wantMapGen.length() > 0) {
                    if (obj.getClass()
                        .getName()
                        .equals(wantMapGen)
                        || obj.getClass()
                            .getSimpleName()
                            .equals(wantMapGen)) {
                        return (MapGenStructure) obj;
                    }
                    continue;
                }

                // 原版结构
                if ("minecraft:village".equals(want) && obj instanceof MapGenVillage) return (MapGenStructure) obj;
                if ("minecraft:stronghold".equals(want) && obj instanceof MapGenStronghold)
                    return (MapGenStructure) obj;
                if ("minecraft:mineshaft".equals(want) && obj instanceof MapGenMineshaft) return (MapGenStructure) obj;

                // temple共用一个MapGen
                if (("minecraft:desert_pyramid".equals(want) || "minecraft:jungle_pyramid".equals(want)
                    || "minecraft:swamp_hut".equals(want)) && obj instanceof MapGenScatteredFeature) {
                    return (MapGenStructure) obj;
                }

                // 下界
                if ("minecraft:fortress".equals(want) && obj instanceof MapGenNetherBridge)
                    return (MapGenStructure) obj;
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

            for (Method m : MapGenStructure.class.getDeclaredMethods()) {
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

    /**
     * 出当前维度里所有MapGenStructure
     */
    public static List<String> debugListStructureGenerators(World world) {
        if (world == null) return null;
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;

        List<String> out = new ArrayList<String>();
        try {
            ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();
            IChunkProvider provider = server.currentChunkProvider;
            if (provider == null) return out;

            for (Field f : provider.getClass()
                .getDeclaredFields()) {
                f.setAccessible(true);
                Object obj = f.get(provider);
                if (!(obj instanceof MapGenStructure)) continue;
                out.add(
                    "[LocateLegacy] dim=" + world.provider.dimensionId
                        + " field="
                        + provider.getClass()
                            .getSimpleName()
                        + "."
                        + f.getName()
                        + " -> "
                        + obj.getClass()
                            .getName());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return out;
    }

    public static boolean isStructureSupportedInWorld(World world, String structureId) {
        if (world == null || structureId == null) return false;

        StructureDefinition def = StructureConfigManager.get(structureId.toLowerCase());
        if (def != null && def.dim != world.provider.dimensionId) return false;

        return findGenerator(world, structureId) != null;
    }

    public static List<String> getAvailableStructureIds(World world) {
        if (world == null) return java.util.Collections.emptyList();

        java.util.ArrayList<String> out = new java.util.ArrayList<String>();

        for (String id : VANILLA_IDS) {
            if (isStructureSupportedInWorld(world, id)) out.add(id);
        }

        java.util.List<StructureDefinition> defs = StructureConfigManager.getEntriesForDim(world.provider.dimensionId);
        if (defs != null && !defs.isEmpty()) {
            for (StructureDefinition d : defs) {
                if (d == null || !d.isValid() || d.id == null) continue;
                String fid = d.fullId();
                if (isStructureSupportedInWorld(world, fid)) {
                    out.add(fid.toLowerCase());
                }
            }
        }

        return out;
    }
}
