package com.example.locateleagcy.locate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class BiomeLocator {

    private static final int STEP_RADIUS = 1024;
    private static final int MAX_RADIUS = 8192;

    public static int[] locate(World world, String biomeName, int blockX, int blockZ) {

        BiomeGenBase target = findBiomeByName(biomeName);
        if (target == null) return null;

        // 1️⃣ 当前位置检测
        BiomeGenBase current = world.getBiomeGenForCoords(blockX, blockZ);

        if (current == target) {
            return new int[] { blockX, blockZ };
        }

        List<BiomeGenBase> searchList = new ArrayList<BiomeGenBase>();
        searchList.add(target);

        Random random = new Random(world.getSeed());

        try {

            // 2️⃣ 逐步扩大半径
            for (int radius = STEP_RADIUS; radius <= MAX_RADIUS; radius += STEP_RADIUS) {

                ChunkPosition pos = world.getWorldChunkManager()
                    .findBiomePosition(blockX, blockZ, radius, searchList, random);

                if (pos != null) {

                    return new int[] { pos.chunkPosX, pos.chunkPosZ };
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return null;
    }

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
