package com.example.locatelegacy.config;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class BiomeWalkTracker {

    private final Map<String, Long> lastByPlayer = new HashMap<String, Long>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        EntityPlayer p = e.player;
        if (p == null) return;

        World w = p.worldObj;
        if (w == null || w.isRemote) return;

        int x = (int) Math.floor(p.posX);
        int z = (int) Math.floor(p.posZ);

        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        if (b == null) return;

        int dim = w.provider.dimensionId;
        int biomeId = b.biomeID;

        String key = p.getCommandSenderName();
        long packed = pack(dim, biomeId);
        Long old = lastByPlayer.get(key);
        if (old != null && old.longValue() == packed) return;

        lastByPlayer.put(key, Long.valueOf(packed));

        BiomeListManager.recordBiome(dim, biomeId, b.biomeName);
    }

    private static long pack(int dim, int biomeId) {
        return (((long) dim) << 32) ^ (biomeId & 0xFFFFFFFFL);
    }
}
