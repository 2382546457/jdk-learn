package org.xiaohe;

import java.util.concurrent.CountDownLatch;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-18 13:53
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(() -> {
                System.out.println("开始");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();

    }
}