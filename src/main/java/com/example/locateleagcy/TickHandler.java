package com.example.locateleagcy;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.locateleagcy.locate.LocateTaskManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 服务器 Tick 驱动器：
 * 1) 提供 runOnMainThread：让后台线程安全地把“发消息”等操作切回主线程
 * 2) 每 tick 驱动 LocateTaskManager：分帧执行 locate 检查，避免卡服
 */
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

        // 先跑 locate 任务（每 tick 只做少量工作）
        LocateTaskManager.tick();

        // 再执行主线程回调队列（避免队列堆积）
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
