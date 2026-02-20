package com.example.locateleagcy.locate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * biome 工具：
 * - 主世界：Tab 用全局列表（恢复原来）
 * - 非主世界：Tab 只用“已加载区块真实出现过”的 biome（避免 spawnList 被污染）
 * - 非主世界：执行 locate 前，如果该 biome 从未在已加载区块出现过 → 直接拒绝（避免永不返回）
 */
public class BiomeLocator {

    private static final int SAMPLE_RADIUS_CHUNKS = 10; // 10 chunks = 160 blocks
    private static final int SAMPLE_STEP = 4; // 每区块采样步长(0,4,8,12) => 16点/区块，轻量

    /** 主世界 Tab：全局 biome 名列表（原来的实现） */
    public static List<String> getAllBiomeNames() {

        List<String> names = new ArrayList<String>();

        BiomeGenBase[] biomes = BiomeGenBase.getBiomeGenArray();
        for (BiomeGenBase b : biomes) {
            if (b == null || b.biomeName == null) continue;
            names.add(b.biomeName);
        }

        return names;
    }

    /** 非主世界 Tab：只取“已加载区块里真实出现过”的 biome 名 */
    public static List<String> getObservedBiomeNames(World world, EntityPlayer player) {

        Set<String> out = new HashSet<String>();

        if (world == null || player == null) return new ArrayList<String>();

        int pcx = ((int) player.posX) >> 4;
        int pcz = ((int) player.posZ) >> 4;

        for (int dcx = -SAMPLE_RADIUS_CHUNKS; dcx <= SAMPLE_RADIUS_CHUNKS; dcx++) {
            for (int dcz = -SAMPLE_RADIUS_CHUNKS; dcz <= SAMPLE_RADIUS_CHUNKS; dcz++) {

                int cx = pcx + dcx;
                int cz = pcz + dcz;

                // ✅ 关键：只看已存在区块，绝不触发加载/生成
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) continue;

                int baseX = cx << 4;
                int baseZ = cz << 4;

                for (int ox = 0; ox < 16; ox += SAMPLE_STEP) {
                    for (int oz = 0; oz < 16; oz += SAMPLE_STEP) {

                        BiomeGenBase b = world.getBiomeGenForCoords(baseX + ox, baseZ + oz);
                        if (b == null || b.biomeName == null) continue;

                        out.add(b.biomeName);
                    }
                }
            }
        }

        return new ArrayList<String>(out);
    }

    /** 非主世界：判断某 biome 是否已在附近已加载区块“真实出现过” */
    public static boolean isBiomeObserved(World world, EntityPlayer player, BiomeGenBase biome) {

        if (biome == null || biome.biomeName == null) return false;

        List<String> observed = getObservedBiomeNames(world, player);
        for (String n : observed) {
            if (n != null && n.equalsIgnoreCase(biome.biomeName)) return true;
        }
        return false;
    }

    /** 全局表宽松匹配（给主世界用：恢复以前体验） */
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

    /** 非主世界：只在 observed 列表里匹配（避免“脏 spawnList”） */
    public static BiomeGenBase findBiomeByNameObserved(World world, EntityPlayer player, String name) {

        if (world == null || player == null || name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        List<String> observed = getObservedBiomeNames(world, player);

        // 精确匹配
        for (String n : observed) {
            if (n != null && n.equalsIgnoreCase(q)) {
                return findBiomeByNameGlobal(n); // 用全局表拿实例
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
