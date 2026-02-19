package com.example.locateleagcy.locate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class BiomeLocator {

    // =============================
    // 主 locate 方法
    // =============================
    public static int[] locate(World world, String biomeName, int blockX, int blockZ) {

        BiomeGenBase target = findBiomeByName(biomeName);

        if (target == null) return null;

        List<BiomeGenBase> searchList = new ArrayList<BiomeGenBase>();

        searchList.add(target);

        try {

            ChunkPosition pos = world.getWorldChunkManager()
                .findBiomePosition(
                    blockX,
                    blockZ,
                    4000, // 搜索半径（block单位）
                    searchList,
                    world.rand);

            if (pos == null) return null;

            return new int[] { pos.chunkPosX, pos.chunkPosZ };

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =============================
    // 查找 biome（模糊匹配）
    // =============================
    private static BiomeGenBase findBiomeByName(String name) {

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();

        for (BiomeGenBase biome : biomes) {

            if (biome == null) continue;

            if (biome.biomeName.equalsIgnoreCase(name)) return biome;

            if (biome.biomeName.toLowerCase()
                .contains(name.toLowerCase())) return biome;
        }

        return null;
    }
}
