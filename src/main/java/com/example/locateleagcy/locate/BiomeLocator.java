package com.example.locateleagcy.locate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.example.locateleagcy.locate.LocateTaskManager.BiomeMatch;

/**
 * 群系工具：
 * - 支持模糊匹配（contains）
 * - 支持精确匹配（equalsIgnoreCase）
 * - 给 tab 补全提供列表
 */
public class BiomeLocator {

    public static BiomeMatch resolveBiome(String name) {

        if (name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

        // 1) 精确匹配优先
        for (BiomeGenBase b : biomes) {
            if (b == null) continue;
            if (b.biomeName.equalsIgnoreCase(q)) {
                return new BiomeMatch(b, b.biomeName);
            }
        }

        // 2) 模糊 contains
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
