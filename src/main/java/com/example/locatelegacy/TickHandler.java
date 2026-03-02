package com.example.locatelegacy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.locatelegacy.locate.LocateTaskManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class TickHandler {

    private static volatile long serverTicks = 0L;

    private static final Queue<Runnable> MAIN_THREAD_QUEUE = new ConcurrentLinkedQueue<Runnable>();

    public static long getServerTicks() {
        return serverTicks;
    }

    public static void runOnMainThread(Runnable r) {
        if (r != null) MAIN_THREAD_QUEUE.add(r);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        serverTicks++;

        for (;;) {
            Runnable r = MAIN_THREAD_QUEUE.poll();
            if (r == null) break;
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        try {
            LocateTaskManager.tick();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
