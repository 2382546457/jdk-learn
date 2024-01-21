package org.xiaohe.use.有返回值;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-20 15:27
 */
public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        AddTask addTask = new AddTask(0, 100);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ForkJoinTask<Integer> submit = forkJoinPool.submit(addTask);
        Integer sum = submit.get();
        System.out.println(sum);

        forkJoinPool.shutdown();
    }
}
