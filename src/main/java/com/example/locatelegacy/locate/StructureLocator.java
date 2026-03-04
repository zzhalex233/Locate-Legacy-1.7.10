package com.example.locatelegacy.locate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.BiomeDictionary;

import com.example.locatelegacy.config.StructureConfigManager;
import com.example.locatelegacy.config.StructureDefinition;
import com.example.locatelegacy.util.LogUtil;

public class StructureLocator {

    private static volatile Method canSpawnMethod;

    private static final Map<String, MapGenStructure> GEN_CACHE = new ConcurrentHashMap<String, MapGenStructure>();
    private static final Map<String, Integer> DIAMETER_CACHE = new ConcurrentHashMap<String, Integer>();
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int DIAMETER_CACHE_SOFT_LIMIT = 20000;
    private static final int DIAMETER_CACHE_TRIM_TO = 16000;

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
            if (gen instanceof MapGenScatteredFeature
                && !scatteredTypeMatch(world, structureId.toLowerCase(), chunkX, chunkZ)) {
                return false;
            }

            // mod：同一个 MapGen 可能用于生成多种结构（比如沟槽的暮色森林），过滤
            StructureDefinition def = StructureConfigManager.get(structureId.toLowerCase());
            if (def != null) {
                int x = (chunkX << 4) + 8;
                int z = (chunkZ << 4) + 8;
                BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
                if (biome == null) return false;

                if (!biomeMatch(def, biome)) return false;

                if (!extraFilterMatch(world, gen, def, chunkX, chunkZ)) return false;
            }

            return true;

        } catch (Throwable t) {
            LogUtil.warn("Failed structure spawn check for " + structureId + " at chunk " + chunkX + "," + chunkZ, t);
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

    private static boolean biomeMatch(StructureDefinition def, BiomeGenBase biome) {
        if (def == null) return true;
        if (biome == null || biome.biomeName == null) return false;
        String biomeName = biome.biomeName.trim().toLowerCase();

        if (def.biomeNameWhitelist != null && !def.biomeNameWhitelist.isEmpty()) {
            boolean ok = false;
            for (String w : def.biomeNameWhitelist) {
                if (w != null && biomeName.equals(w.trim().toLowerCase())) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }

        // all：允许全部。搭配 blacklist,
        // 如果既没写 whitelist 也没写 all，则默认all

        // blacklist：最后排除
        if (def.biomeNameBlacklist != null && !def.biomeNameBlacklist.isEmpty()) {
            for (String b : def.biomeNameBlacklist) {
                if (b != null && biomeName.equals(b.trim().toLowerCase())) return false;
            }
        }

        return true;
    }

    private static boolean extraFilterMatch(World world, MapGenStructure gen, StructureDefinition def, int chunkX,
        int chunkZ) {
        if (def == null) return true;

        boolean hasAny = def.hasHeightRangeFilter() || def.hasOccupiedChunkDiameterFilter();
        if (!hasAny) return true;

        boolean heightOk = true;
        if (def.hasHeightRangeFilter()) {
            Integer y = tryGetSurfaceY(world, chunkX, chunkZ);
            if (y == null) {
                heightOk = allowUnknown(def.heightUnknownPolicy);
            } else {
                int min = def.heightMinY != null ? def.heightMinY.intValue() : Integer.MIN_VALUE;
                int max = def.heightMaxY != null ? def.heightMaxY.intValue() : Integer.MAX_VALUE;
                heightOk = y.intValue() >= min && y.intValue() <= max;
            }
        }

        boolean diameterOk = true;
        if (def.hasOccupiedChunkDiameterFilter()) {
            int d = getOccupiedChunkDiameter(world, gen, chunkX, chunkZ);
            if (d == UNKNOWN) {
                diameterOk = allowUnknown(def.diameterUnknownPolicy);
            } else {
                int min = def.occupiedChunkDiameterMin != null ? def.occupiedChunkDiameterMin.intValue()
                    : Integer.MIN_VALUE;
                int max = def.occupiedChunkDiameterMax != null ? def.occupiedChunkDiameterMax.intValue()
                    : Integer.MAX_VALUE;
                diameterOk = d >= min && d <= max;
            }
        }

        if (def.filterStrict) {
            return heightOk && diameterOk;
        }
        return heightOk || diameterOk;
    }

    private static boolean allowUnknown(String policy) {
        return !"fail".equalsIgnoreCase(policy);
    }

    private static Integer tryGetSurfaceY(World world, int chunkX, int chunkZ) {
        if (world == null) return null;
        if (!world.getChunkProvider()
            .chunkExists(chunkX, chunkZ)) return null;
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        try {
            return Integer.valueOf(world.getTopSolidOrLiquidBlock(x, z));
        } catch (Throwable t) {
            return null;
        }
    }

    private static int getOccupiedChunkDiameter(World world, MapGenStructure gen, int chunkX, int chunkZ) {
        if (world == null || gen == null) return UNKNOWN;
        if (!world.getChunkProvider()
            .chunkExists(chunkX, chunkZ)) return UNKNOWN;

        String key = world.provider.dimensionId + "|"
            + gen.getClass()
                .getName()
            + "|"
            + chunkX
            + "|"
            + chunkZ;
        Integer cached = DIAMETER_CACHE.get(key);
        if (cached != null) return cached.intValue();

        int value = findOccupiedChunkDiameter(world, gen, chunkX, chunkZ);
        trimDiameterCacheIfNeeded();
        DIAMETER_CACHE.put(key, Integer.valueOf(value));
        return value;
    }

    private static void trimDiameterCacheIfNeeded() {
        if (DIAMETER_CACHE.size() <= DIAMETER_CACHE_SOFT_LIMIT) return;
        int toRemove = Math.max(1, DIAMETER_CACHE.size() - DIAMETER_CACHE_TRIM_TO);
        for (String k : DIAMETER_CACHE.keySet()) {
            DIAMETER_CACHE.remove(k);
            toRemove--;
            if (toRemove <= 0) break;
        }
    }

    private static int findOccupiedChunkDiameter(World world, MapGenStructure gen, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;

        try {
            for (Field f : MapGenStructure.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object raw = f.get(gen);
                if (!(raw instanceof Map)) continue;

                Map<?, ?> map = (Map<?, ?>) raw;
                for (Object v : map.values()) {
                    if (!(v instanceof StructureStart)) continue;
                    StructureStart ss = (StructureStart) v;
                    if (ss == null || !ss.isSizeableStructure()) continue;

                    StructureBoundingBox bb = ss.getBoundingBox();
                    if (bb == null) continue;

                    if (x < bb.minX || x > bb.maxX || z < bb.minZ || z > bb.maxZ) continue;

                    int minCx = bb.minX >> 4;
                    int maxCx = bb.maxX >> 4;
                    int minCz = bb.minZ >> 4;
                    int maxCz = bb.maxZ >> 4;

                    int dx = maxCx - minCx + 1;
                    int dz = maxCz - minCz + 1;
                    return Math.max(dx, dz);
                }
            }
        } catch (Throwable t) {
            LogUtil.warn("Failed occupied-chunk-diameter probing for " + gen.getClass()
                .getName(), t);
        }
        return UNKNOWN;
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
            LogUtil.warn("Failed to resolve map generator for " + structureId, t);
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
            LogUtil.error("Failed to resolve canSpawnStructureAtCoords method.", t);
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
            LogUtil.warn("Failed to enumerate structure generators.", t);
        }
        return out;
    }

    public static final class DebugStructureInfo {

        public String fullId;
        public int dim;
        public String mapGenClass;
        public String biomeRule;
        public String heightRule;
        public String diameterRule;
        public String sampleStatus;
        public String copyJson;
    }

    public static final class DebugLearnResult {

        public String fullId;
        public int dim;
        public String mapGenClass;
        public String providerField;
        public Integer occupiedChunkDiameter;
        public Integer heightMinY;
        public Integer heightMaxY;
        public long heightSampleSum;
        public int heightSampleCount;
        public List<String> biomeNames = new ArrayList<String>();
        public String copyJson;
        public String note;
    }

    public static List<DebugStructureInfo> debugDescribeStructures(World world, int sampleChunkX, int sampleChunkZ) {
        if (world == null) return Collections.emptyList();

        List<String> ids = getAvailableStructureIds(world);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        Set<String> uniq = new HashSet<String>();
        List<DebugStructureInfo> out = new ArrayList<DebugStructureInfo>();
        for (String id : ids) {
            if (id == null) continue;
            String fid = id.trim()
                .toLowerCase();
            if (fid.length() == 0 || !uniq.add(fid)) continue;

            MapGenStructure gen = getGenerator(world, fid);
            if (gen == null) continue;

            StructureDefinition def = StructureConfigManager.get(fid);
            DebugStructureInfo info = new DebugStructureInfo();
            info.fullId = fid;
            info.dim = world.provider.dimensionId;
            info.mapGenClass = gen.getClass()
                .getName();
            info.biomeRule = formatBiomeRule(def);
            info.heightRule = formatHeightRule(def);
            info.diameterRule = formatDiameterRule(def);
            info.sampleStatus = formatSampleStatus(world, gen, def, sampleChunkX, sampleChunkZ);
            info.copyJson = buildCopyJson(def, fid, info.mapGenClass, info.dim);
            out.add(info);
        }
        return out;
    }

    public static DebugLearnResult debugLearnStructureAt(World world, String fullId, int blockX, int blockZ) {
        if (world == null || fullId == null) return null;
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;

        StructureStartMatch match = findNearestContainingStart(world, blockX, blockZ);
        if (match == null || match.start == null || match.box == null) return null;

        DebugLearnResult r = new DebugLearnResult();
        r.fullId = fullId.toLowerCase();
        r.dim = world.provider.dimensionId;
        r.mapGenClass = match.gen.getClass()
            .getName();
        r.providerField = match.fieldName;

        int minCx = match.box.minX >> 4;
        int maxCx = match.box.maxX >> 4;
        int minCz = match.box.minZ >> 4;
        int maxCz = match.box.maxZ >> 4;
        r.occupiedChunkDiameter = Integer.valueOf(Math.max(maxCx - minCx + 1, maxCz - minCz + 1));

        TreeSet<String> biomeSet = new TreeSet<String>();
        Integer minY = null;
        Integer maxY = null;
        long sumY = 0L;
        int sampled = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) continue;
                int sx = (cx << 4) + 8;
                int sz = (cz << 4) + 8;
                BiomeGenBase b = world.getBiomeGenForCoords(sx, sz);
                if (b != null && b.biomeName != null) biomeSet.add(b.biomeName.trim().toLowerCase());
                try {
                    int y = world.getTopSolidOrLiquidBlock(sx, sz);
                    if (minY == null || y < minY.intValue()) minY = Integer.valueOf(y);
                    if (maxY == null || y > maxY.intValue()) maxY = Integer.valueOf(y);
                    sumY += y;
                } catch (Throwable ignored) {}
                sampled++;
            }
        }
        r.biomeNames.addAll(biomeSet);
        r.heightMinY = minY;
        r.heightMaxY = maxY;
        r.heightSampleSum = sumY;
        r.heightSampleCount = sampled;
        r.note = sampled > 0 ? ("sampledChunks=" + sampled) : "sampledChunks=0(stand closer or load chunks)";
        r.copyJson = buildLearnedJson(r);
        return r;
    }

    private static final class StructureStartMatch {

        MapGenStructure gen;
        String fieldName;
        StructureStart start;
        StructureBoundingBox box;
    }

    private static StructureStartMatch findNearestContainingStart(World world, int blockX, int blockZ) {
        ChunkProviderServer server = (ChunkProviderServer) world.getChunkProvider();
        IChunkProvider provider = server.currentChunkProvider;
        if (provider == null) return null;

        StructureStartMatch best = null;
        int bestArea = Integer.MAX_VALUE;
        try {
            for (Field f : provider.getClass()
                .getDeclaredFields()) {
                f.setAccessible(true);
                Object obj = f.get(provider);
                if (!(obj instanceof MapGenStructure)) continue;
                MapGenStructure gen = (MapGenStructure) obj;

                for (StructureStart ss : listStructureStarts(gen)) {
                    if (ss == null || !ss.isSizeableStructure()) continue;
                    StructureBoundingBox bb = ss.getBoundingBox();
                    if (bb == null) continue;
                    if (blockX < bb.minX || blockX > bb.maxX || blockZ < bb.minZ || blockZ > bb.maxZ) continue;
                    int area = Math.max(1, (bb.maxX - bb.minX + 1)) * Math.max(1, (bb.maxZ - bb.minZ + 1));
                    if (area < bestArea) {
                        bestArea = area;
                        StructureStartMatch m = new StructureStartMatch();
                        m.gen = gen;
                        m.fieldName = f.getName();
                        m.start = ss;
                        m.box = bb;
                        best = m;
                    }
                }
            }
        } catch (Throwable t) {
            LogUtil.warn("Failed to inspect nearby structure starts.", t);
        }
        return best;
    }

    private static List<StructureStart> listStructureStarts(MapGenStructure gen) {
        List<StructureStart> out = new ArrayList<StructureStart>();
        if (gen == null) return out;
        try {
            for (Field f : MapGenStructure.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object raw = f.get(gen);
                if (!(raw instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) raw;
                for (Object v : m.values()) {
                    if (v instanceof StructureStart) out.add((StructureStart) v);
                }
            }
        } catch (Throwable t) {
            LogUtil.warn("Failed to list structure starts for " + gen.getClass()
                .getName(), t);
        }
        return out;
    }

    private static String buildLearnedJson(DebugLearnResult r) {
        String mod = "minecraft";
        String id = r.fullId;
        int p = r.fullId.indexOf(':');
        if (p > 0) {
            mod = r.fullId.substring(0, p);
            id = r.fullId.substring(p + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"")
            .append(id)
            .append("\",\"mod\":\"")
            .append(mod)
            .append("\",\"mapGen\":\"")
            .append(r.mapGenClass)
            .append("\",\"dim\":")
            .append(r.dim);

        if (r.biomeNames != null && !r.biomeNames.isEmpty()) {
            sb.append(",\"biomeNameWhitelist\":[");
            for (int i = 0; i < r.biomeNames.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(r.biomeNames.get(i))).append("\"");
            }
            sb.append("]");
        } else {
            sb.append(",\"biomeName\":\"all\"");
        }

        sb.append(",\"filters\":{\"strict\":true");
        if (r.heightMinY != null || r.heightMaxY != null) {
            sb.append(",\"heightRange\":{\"minY\":")
                .append(r.heightMinY != null ? r.heightMinY.intValue() : 0);
            sb.append(",\"maxY\":")
                .append(r.heightMaxY != null ? r.heightMaxY.intValue() : 255);
            sb.append(",\"unknownPolicy\":\"fail\"}");
        }
        if (r.occupiedChunkDiameter != null) {
            sb.append(",\"occupiedChunkDiameter\":{\"min\":")
                .append(r.occupiedChunkDiameter.intValue());
            sb.append(",\"max\":")
                .append(r.occupiedChunkDiameter.intValue());
            sb.append(",\"unknownPolicy\":\"fail\"}");
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatBiomeRule(StructureDefinition def) {
        if (def == null) return "default";
        if (def.biomeNameWhitelist != null && !def.biomeNameWhitelist.isEmpty())
            return "whitelist=" + def.biomeNameWhitelist;
        if (def.biomeNameBlacklist != null && !def.biomeNameBlacklist.isEmpty())
            return "blacklist=" + def.biomeNameBlacklist;
        return def.biomeAll ? "all" : "default";
    }

    private static String formatHeightRule(StructureDefinition def) {
        if (def == null || !def.hasHeightRangeFilter()) return "none";
        String min = def.heightMinY != null ? String.valueOf(def.heightMinY) : "-inf";
        String max = def.heightMaxY != null ? String.valueOf(def.heightMaxY) : "+inf";
        return "[" + min + "," + max + "] unknown=" + def.heightUnknownPolicy;
    }

    private static String formatDiameterRule(StructureDefinition def) {
        if (def == null || !def.hasOccupiedChunkDiameterFilter()) return "none";
        String min = def.occupiedChunkDiameterMin != null ? String.valueOf(def.occupiedChunkDiameterMin) : "-inf";
        String max = def.occupiedChunkDiameterMax != null ? String.valueOf(def.occupiedChunkDiameterMax) : "+inf";
        return "[" + min + "," + max + "] unknown=" + def.diameterUnknownPolicy;
    }

    private static String formatSampleStatus(World world, MapGenStructure gen, StructureDefinition def, int cx,
        int cz) {
        if (def == null || (!def.hasHeightRangeFilter() && !def.hasOccupiedChunkDiameterFilter()))
            return "filters=none";

        StringBuilder sb = new StringBuilder();
        if (def.hasHeightRangeFilter()) {
            Integer y = tryGetSurfaceY(world, cx, cz);
            if (y == null) {
                sb.append("height=UNKNOWN");
            } else {
                int min = def.heightMinY != null ? def.heightMinY.intValue() : Integer.MIN_VALUE;
                int max = def.heightMaxY != null ? def.heightMaxY.intValue() : Integer.MAX_VALUE;
                boolean pass = y.intValue() >= min && y.intValue() <= max;
                sb.append("height=")
                    .append(y)
                    .append(pass ? "(PASS)" : "(FAIL)");
            }
        }
        if (def.hasOccupiedChunkDiameterFilter()) {
            if (sb.length() > 0) sb.append(" ");
            int d = getOccupiedChunkDiameter(world, gen, cx, cz);
            if (d == UNKNOWN) {
                sb.append("diameter=UNKNOWN");
            } else {
                int min = def.occupiedChunkDiameterMin != null ? def.occupiedChunkDiameterMin.intValue()
                    : Integer.MIN_VALUE;
                int max = def.occupiedChunkDiameterMax != null ? def.occupiedChunkDiameterMax.intValue()
                    : Integer.MAX_VALUE;
                boolean pass = d >= min && d <= max;
                sb.append("diameter=")
                    .append(d)
                    .append(pass ? "(PASS)" : "(FAIL)");
            }
        }
        return sb.toString();
    }

    private static String buildCopyJson(StructureDefinition def, String fid, String mapGen, int dim) {
        String mod = "minecraft";
        String id = fid;
        int p = fid.indexOf(':');
        if (p > 0) {
            mod = fid.substring(0, p);
            id = fid.substring(p + 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"")
            .append(id)
            .append("\",\"mod\":\"")
            .append(mod)
            .append("\",\"mapGen\":\"")
            .append(mapGen)
            .append("\",\"dim\":")
            .append(dim);
        if (def != null) {
            if (def.biomeNameWhitelist != null && !def.biomeNameWhitelist.isEmpty()) {
                sb.append(",\"biomeNameWhitelist\":[");
                for (int i = 0; i < def.biomeNameWhitelist.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(def.biomeNameWhitelist.get(i))).append("\"");
                }
                sb.append("]");
            } else if (def.biomeNameBlacklist != null && !def.biomeNameBlacklist.isEmpty()) {
                sb.append(",\"biomeName\":\"all\"");
                sb.append(",\"biomeNameBlacklist\":[");
                for (int i = 0; i < def.biomeNameBlacklist.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(def.biomeNameBlacklist.get(i))).append("\"");
                }
                sb.append("]");
            } else if (def.biomeAll) {
                sb.append(",\"biomeName\":\"all\"");
            }
        }
        if (def != null) {
            if (def.hasHeightRangeFilter()) {
                sb.append(",\"filters\":{\"strict\":")
                    .append(def.filterStrict);
                sb.append(",\"heightRange\":{\"minY\":")
                    .append(def.heightMinY != null ? def.heightMinY.intValue() : 0);
                sb.append(",\"maxY\":")
                    .append(def.heightMaxY != null ? def.heightMaxY.intValue() : 255);
                sb.append(",\"unknownPolicy\":\"")
                    .append(def.heightUnknownPolicy)
                    .append("\"}");
                if (def.hasOccupiedChunkDiameterFilter()) {
                    sb.append(",\"occupiedChunkDiameter\":{\"min\":")
                        .append(def.occupiedChunkDiameterMin != null ? def.occupiedChunkDiameterMin.intValue() : 0);
                    sb.append(",\"max\":")
                        .append(def.occupiedChunkDiameterMax != null ? def.occupiedChunkDiameterMax.intValue() : 4096);
                    sb.append(",\"unknownPolicy\":\"")
                        .append(def.diameterUnknownPolicy)
                        .append("\"}");
                }
                sb.append("}");
            } else if (def.hasOccupiedChunkDiameterFilter()) {
                sb.append(",\"filters\":{\"strict\":")
                    .append(def.filterStrict);
                sb.append(",\"occupiedChunkDiameter\":{\"min\":")
                    .append(def.occupiedChunkDiameterMin != null ? def.occupiedChunkDiameterMin.intValue() : 0);
                sb.append(",\"max\":")
                    .append(def.occupiedChunkDiameterMax != null ? def.occupiedChunkDiameterMax.intValue() : 4096);
                sb.append(",\"unknownPolicy\":\"")
                    .append(def.diameterUnknownPolicy)
                    .append("\"}}");
            }
        }
        sb.append("}");
        return sb.toString();
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
