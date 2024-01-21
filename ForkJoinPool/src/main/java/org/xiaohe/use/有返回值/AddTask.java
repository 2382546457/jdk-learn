package org.xiaohe.use.有返回值;

import java.util.concurrent.RecursiveTask;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-20 15:21
 */
public class AddTask extends RecursiveTask<Integer> {
    /**
     * 一次最多10个数相加
     */
    private static final int THRESHOLD = 10;

    private int start;
    private int end;

    public AddTask(int start, int end) {
        super();
        this.start = start;
        this.end = end;
    }

    @Override
    protected Integer compute() {
        int sum = 0;
        if (end - start < THRESHOLD) {
            for (int i = start; i < end; i++) {
                sum += i;
            }
            return sum;
        } else {
            int middle = (start + end) / 2;
            AddTask left = new AddTask(start, middle);
            AddTask right = new AddTask(middle, end);
            left.fork();
            right.fork();

            return left.join() + right.join();
        }
    }
}
