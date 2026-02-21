package com.example.locatelegacy;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.locatelegacy.locate.LocateTaskManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class TickHandler {

    private static final ConcurrentLinkedQueue<Runnable> MAIN_QUEUE = new ConcurrentLinkedQueue<Runnable>();

    private static long serverTicks = 0L;

    public static long getServerTicks() {
        return serverTicks;
    }

    public static void runOnMainThread(Runnable r) {
        if (r != null) MAIN_QUEUE.add(r);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;

        serverTicks++;

        LocateTaskManager.tick();

        int max = 200;
        while (max-- > 0) {
            Runnable r = MAIN_QUEUE.poll();
            if (r == null) break;
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
