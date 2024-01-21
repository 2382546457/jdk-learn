package org.xiaohe.use.无返回值;

import java.util.concurrent.RecursiveAction;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-20 15:14
 */
public class PrintTask extends RecursiveAction {
    /**
     * 一次最多打印50个数
     */
    private static final int THRESHOLD = 50;

    private int start;
    private int end;

    public PrintTask(int start, int end) {
        super();
        this.start = start;
        this.end = end;
    }

    @Override
    protected void compute() {
        if (end - start < THRESHOLD) {
            System.out.println(Thread.currentThread().getName() + " 可以打印 " + start + " - " + end);
            for (int i = start; i < end; i++) {
                System.out.println(Thread.currentThread().getName() + " 打印了 " + i);
            }
        } else {
            int middle = (start + end) / 2;
            PrintTask leftTask = new PrintTask(start, middle);
            PrintTask rightTask = new PrintTask(middle, end);
            // 执行两个小任务
            leftTask.fork();
            rightTask.fork();
        }
    }
}
