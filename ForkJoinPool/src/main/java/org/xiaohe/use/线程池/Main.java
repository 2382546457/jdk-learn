package org.xiaohe.use.线程池;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-21 16:33
 */
public class Main {
    public static void main(String[] args) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 10, 10,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        executor.execute(() -> {
            try {
                Thread.sleep(100 * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        executor.execute(() -> {
            System.out.println(111);
        });
        executor.execute(() -> {
            System.out.println(111);
        });
        executor.shutdown();
    }
}
