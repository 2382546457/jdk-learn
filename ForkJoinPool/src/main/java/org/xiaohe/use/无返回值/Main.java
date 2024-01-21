package org.xiaohe.use.无返回值;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-20 15:11
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        PrintTask printTask = new PrintTask(0, 300);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.submit(printTask);

        // 线程阻塞，等待任务执行，最多阻塞两秒
        forkJoinPool.awaitTermination(2, TimeUnit.SECONDS);
        forkJoinPool.shutdown();
    }
}