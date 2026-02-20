package com.example.locateleagcy.locate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocateExecutor {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    public static void submit(Runnable r) {
        POOL.submit(r);
    }
}
