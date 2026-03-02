package com.example.locatelegacy.locate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.example.locatelegacy.config.BiomeListManager;

public class BiomeLocator {

    private static final int SAMPLE_RADIUS_CHUNKS = 10;
    private static final int SAMPLE_STEP = 4;

    public static List<String> getAllBiomeNames() {

        List<String> names = new ArrayList<String>();

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();
        for (BiomeGenBase b : biomes) {
            if (b == null || b.biomeName == null) continue;
            names.add(b.biomeName);
        }

        return names;
    }

    public static List<String> getObservedBiomeNames(World world, EntityPlayer player) {

        Set<String> out = new HashSet<String>();

        if (world == null || player == null) return new ArrayList<String>();

        int dim = world.provider.dimensionId;
        List<String> persisted = BiomeListManager.getBiomeNamesForDim(dim);
        if (persisted != null) out.addAll(persisted);

        int pcx = ((int) player.posX) >> 4;
        int pcz = ((int) player.posZ) >> 4;

        for (int dcx = -SAMPLE_RADIUS_CHUNKS; dcx <= SAMPLE_RADIUS_CHUNKS; dcx++) {
            for (int dcz = -SAMPLE_RADIUS_CHUNKS; dcz <= SAMPLE_RADIUS_CHUNKS; dcz++) {

                int cx = pcx + dcx;
                int cz = pcz + dcz;

                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) continue;

                int baseX = cx << 4;
                int baseZ = cz << 4;

                for (int ox = 0; ox < 16; ox += SAMPLE_STEP) {
                    for (int oz = 0; oz < 16; oz += SAMPLE_STEP) {

                        BiomeGenBase b = world.getBiomeGenForCoords(baseX + ox, baseZ + oz);
                        if (b == null || b.biomeName == null) continue;

                        out.add(b.biomeName);

                        // 顺手把采样到的群系写入json
                        BiomeListManager.recordBiome(dim, b.biomeID, b.biomeName);
                    }
                }
            }
        }

        return new ArrayList<String>(out);
    }

    public static boolean isBiomeObserved(World world, EntityPlayer player, BiomeGenBase biome) {

        if (biome == null || biome.biomeName == null) return false;

        List<String> observed = getObservedBiomeNames(world, player);
        for (String n : observed) {
            if (n != null && n.equalsIgnoreCase(biome.biomeName)) return true;
        }
        return false;
    }

    public static BiomeGenBase findBiomeByNameGlobal(String name) {

        if (name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

        for (BiomeGenBase b : biomes) {
            if (b == null || b.biomeName == null) continue;
            if (b.biomeName.equalsIgnoreCase(q)) return b;
        }

        String low = q.toLowerCase();
        for (BiomeGenBase b : biomes) {
            if (b == null || b.biomeName == null) continue;
            if (b.biomeName.toLowerCase()
                .contains(low)) return b;
        }

        return null;
    }

    public static BiomeGenBase findBiomeByNameObserved(World world, EntityPlayer player, String name) {

        if (world == null || player == null || name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        List<String> observed = getObservedBiomeNames(world, player);

        // 精确匹配
        for (String n : observed) {
            if (n != null && n.equalsIgnoreCase(q)) {
                return findBiomeByNameGlobal(n);
            }
        }

        // 模糊匹配
        String low = q.toLowerCase();
        for (String n : observed) {
            if (n != null && n.toLowerCase()
                .contains(low)) {
                return findBiomeByNameGlobal(n);
            }
        }

        return null;
    }
}
