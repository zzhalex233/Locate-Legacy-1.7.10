package com.example.locatelegacy.config;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class BiomeWalkTracker {

    private int lastDim = Integer.MIN_VALUE;
    private int lastBiome = Integer.MIN_VALUE;

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

        if (dim == lastDim && biomeId == lastBiome) return;

        lastDim = dim;
        lastBiome = biomeId;

        BiomeListManager.recordBiome(dim, biomeId, b.biomeName);
    }
}
