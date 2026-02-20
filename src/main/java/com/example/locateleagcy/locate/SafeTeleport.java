package com.example.locateleagcy.locate;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * 计算安全传送坐标（Forge 1.7.10 可用）
 *
 * 保留旧行为：
 * - ocean/流体上方会自动找最近陆地（会调整 X/Z）
 *
 * 修复：
 * - 下界/无天空维度不再使用 getTopSolidOrLiquidBlock（避免顶层基岩/基岩层）
 * 改为在 y≈64 附近寻找“地表型落点”的 baseY
 */
public class SafeTeleport {

    private SafeTeleport() {}

    /**
     * 返回安全传送坐标 {x, y, z}
     */
    public static int[] findSafeTeleport(World world, int x, int z, EntityPlayer player) {

        int baseY = getBaseY(world, x, z, player);

        // 判断目标点是否“在流体上”（顶层是液体 或 脚下是液体）
        if (isLiquidTopOrBelow(world, x, baseY, z)) {

            // 在附近找最近陆地（限制半径，避免太重）
            int[] land = findNearestLand(world, x, z, player, 48);
            if (land != null) {
                int lx = land[0];
                int lz = land[1];
                int ly = findSafeY(world, lx, lz, player);
                return new int[] { lx, ly, lz };
            }

            // 找不到陆地就退化为原点安全 y
            int fallbackY = findSafeY(world, x, z, player);
            return new int[] { x, fallbackY, z };
        }

        int y = findSafeY(world, x, z, player);
        return new int[] { x, y, z };
    }

    /**
     * ✅ 关键修复点：获取 baseY
     * - 主世界/有天空维度：沿用 getTopSolidOrLiquidBlock（保留你之前成功的行为）
     * - 无天空维度（下界/部分模组维度）：改为 preferY=64 附近找“地表型落点”
     */
    private static int getBaseY(World world, int x, int z, EntityPlayer player) {

        if (world == null) return 64;

        int minY = 1;
        int maxY = world.getActualHeight() - 2;

        // 下界/无天空：不要最高点算法
        if (world.provider != null && world.provider.hasNoSky) {

            int prefer = 64;
            if (player != null && player.worldObj == world) {
                // 如果玩家当前在这个维度，可稍微靠近玩家高度，但仍以 64 为主
                int py = (int) Math.floor(player.posY);
                if (py > 20 && py < 110) prefer = py;
            }

            prefer = clamp(prefer, minY, maxY);

            // 在 prefer 附近找一个“像地表”的站点：能站、且不要在流体上
            int range = 48;
            for (int d = 0; d <= range; d++) {

                int y1 = prefer + d;
                if (y1 <= maxY && isGoodBase(world, x, y1, z)) return y1;

                int y2 = prefer - d;
                if (y2 >= minY && isGoodBase(world, x, y2, z)) return y2;
            }

            // 找不到就返回 prefer（后续 findSafeY 会再修正）
            return prefer;
        }

        // 有天空维度：沿用旧逻辑
        int top = world.getTopSolidOrLiquidBlock(x, z);
        return clamp(top, minY, maxY);
    }

    /**
     * “适合作为 baseY 的位置”：
     * - 当前位置或脚下不是液体（避免水面/岩浆面）
     * - 并且附近能找到站立点（用 isSafeStand 判断）
     */
    private static boolean isGoodBase(World world, int x, int y, int z) {

        int yy = clamp(y, 1, world.getActualHeight() - 2);

        if (isLiquidTopOrBelow(world, x, yy, z)) return false;

        // 允许稍微上下挪动一点点，找到可站点
        int up = searchUp(world, x, yy, z, 8);
        if (up != -1) return true;

        int down = searchDown(world, x, yy, z, 16);
        return down != -1;
    }

    /**
     * 找到 (x,z) 附近的安全 y（不改 x/z）
     */
    private static int findSafeY(World world, int x, int z, EntityPlayer player) {

        int y = getBaseY(world, x, z, player);

        // 先向上找
        int up = searchUp(world, x, y, z, 32);
        if (up != -1) return up;

        // 再向下找
        int down = searchDown(world, x, y, z, 48);
        if (down != -1) return down;

        return clamp(y, 10, world.getActualHeight() - 2);
    }

    /**
     * 在附近找“最近陆地” (返回 {x,z})
     */
    private static int[] findNearestLand(World world, int x, int z, EntityPlayer player, int maxRadiusBlocks) {

        for (int r = 1; r <= maxRadiusBlocks; r++) {

            for (int dx = -r; dx <= r; dx++) {
                int x1 = x + dx;

                int z1 = z - r;
                if (isLandSafe(world, x1, z1, player)) return new int[] { x1, z1 };

                int z2 = z + r;
                if (isLandSafe(world, x1, z2, player)) return new int[] { x1, z2 };
            }

            for (int dz = -r + 1; dz <= r - 1; dz++) {
                int z1 = z + dz;

                int x1 = x - r;
                if (isLandSafe(world, x1, z1, player)) return new int[] { x1, z1 };

                int x2 = x + r;
                if (isLandSafe(world, x2, z1, player)) return new int[] { x2, z1 };
            }
        }

        return null;
    }

    /**
     * 判定一个 (x,z) 是否有“陆地安全落点”
     */
    private static boolean isLandSafe(World world, int x, int z, EntityPlayer player) {

        int y = getBaseY(world, x, z, player);

        if (isLiquidTopOrBelow(world, x, y, z)) {
            return false;
        }

        int up = searchUp(world, x, y, z, 8);
        if (up != -1) return true;

        int down = searchDown(world, x, y, z, 16);
        return down != -1;
    }

    /**
     * 判断：目标点的“顶层位置”或“脚下”是否为液体
     */
    private static boolean isLiquidTopOrBelow(World world, int x, int y, int z) {

        // y 可能落在空气/雪层等，做一个小范围容错检查，避免 ocean 漏判
        for (int dy = -2; dy <= 2; dy++) {
            int yy = y + dy;

            if (yy < 1 || yy >= world.getActualHeight() - 1) continue;

            Block b = world.getBlock(x, yy, z);
            if (b != null && b.getMaterial() != null
                && b.getMaterial()
                    .isLiquid()) {
                return true;
            }
        }

        // 再额外检查脚下更深一格（有些情况下 top-1 不是液体但 top-2 是液体）
        Block below2 = world.getBlock(x, y - 2, z);
        if (below2 != null && below2.getMaterial() != null
            && below2.getMaterial()
                .isLiquid()) {
            return true;
        }

        return false;
    }

    private static int searchUp(World world, int x, int startY, int z, int maxSteps) {

        int maxY = world.getActualHeight() - 2;
        int y = clamp(startY, 1, maxY);

        for (int i = 0; i <= maxSteps && y + i <= maxY; i++) {
            int ty = y + i;
            if (isSafeStand(world, x, ty, z)) return ty;
        }
        return -1;
    }

    private static int searchDown(World world, int x, int startY, int z, int maxSteps) {

        int y = clamp(startY, 1, world.getActualHeight() - 2);

        for (int i = 0; i <= maxSteps && y - i >= 1; i++) {
            int ty = y - i;
            if (isSafeStand(world, x, ty, z)) return ty;
        }
        return -1;
    }

    /**
     * 判断玩家站在 (x, y, z) 是否安全
     */
    private static boolean isSafeStand(World world, int x, int y, int z) {

        if (y < 2 || y >= world.getActualHeight() - 1) return false;

        Block below = world.getBlock(x, y - 1, z);
        Block feet = world.getBlock(x, y, z);
        Block head = world.getBlock(x, y + 1, z);

        if (below == null || below.isAir(world, x, y - 1, z)) return false;
        if (below.getMaterial() != null && below.getMaterial()
            .isLiquid()) return false;
        if (below.getMaterial() != null && below.getMaterial()
            .isReplaceable()) return false;

        if (!isPassable(world, feet, x, y, z)) return false;
        if (!isPassable(world, head, x, y + 1, z)) return false;

        return true;
    }

    private static boolean isPassable(World world, Block block, int x, int y, int z) {

        if (block == null) return true;
        if (block.isAir(world, x, y, z)) return true;

        return block.getMaterial() != null && block.getMaterial()
            .isReplaceable();
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
