package com.example.locateleagcy.locate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.example.locateleagcy.locate.LocateTaskManager.BiomeMatch;

public class BiomeLocator {

    public static BiomeMatch resolveBiome(String name) {

        if (name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

        for (BiomeGenBase b : biomes) {
            if (b == null) continue;
            if (b.biomeName.equalsIgnoreCase(q)) {
                return new BiomeMatch(b, b.biomeName);
            }
        }

        String low = q.toLowerCase();
        for (BiomeGenBase b : biomes) {
            if (b == null) continue;
            if (b.biomeName.toLowerCase()
                .contains(low)) {
                return new BiomeMatch(b, b.biomeName);
            }
        }

        return null;
    }

    public static List<String> allBiomeNames() {

        List<String> names = new ArrayList<String>();

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();
        for (BiomeGenBase b : biomes) {
            if (b == null) continue;
            names.add(b.biomeName);
        }

        return names;
    }
}
