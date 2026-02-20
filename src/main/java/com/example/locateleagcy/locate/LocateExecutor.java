package com.example.locateleagcy.locate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 固定线程池：只做“纯计算”，不要在这里直接访问 world/chunk（线程不安全）
 */
public class LocateExecutor {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    public static void submit(Runnable r) {
        POOL.submit(r);
    }
}
