package org.xiaohe.timer;

import java.util.Arrays;

/**
 * @author : 小何
 * @Description : 存放任务的数组。小根堆
 * @date : 2024-01-18 14:44
 */
public class TaskQueue {
    /**
     * 存放任务的容器，使用数组实现堆结构
     * 下标 0 不放元素
     */
    private TimerTask[] queue = new TimerTask[128];

    private int size = 0;

    void add(TimerTask task) {
        // 满了就扩容 size *= 2
        if (size + 1 == queue.length) {
            queue = Arrays.copyOf(queue, 2 * queue.length);
        }
        queue[++size] = task;
        // 将这个任务放到合适的地方
        fixUp(size);
    }

    /**
     * 将数组首元素删除
     */
    public void removeMin() {
        // 把最后一个移到第一个，然后将其沉下去
        queue[1] = queue[size];
        queue[size] = null;
        size--;
        fixDown(1);
    }

    public void quickRemove(int i) {
        assert i <= size;
        queue[i] = queue[size];
        queue[size] = null;
        size--;
    }

    /**
     * 给第一个任务重新设置执行时间
     * @param newTime
     */
    public void rescheduleMin(long newTime) {
        queue[1].nextExecutionTime = newTime;
        fixDown(1);
    }

    private void fixUp(int k) {
        while (k > 1) {
            int j = k >> 1;
            long parentTime = queue[j].nextExecutionTime;
            long childTime = queue[k].nextExecutionTime;
            if (parentTime <= childTime) {
                break;
            }
            TimerTask temp = queue[j];
            queue[j] = queue[k];
            queue[k] = temp;
            k = j;
        }
    }

    private void fixDown(int k) {
        int j;
        while ((j = k << 1) <= size && j > 0) {
            // k 是 j 的 父亲。
            // 看看 j 和 j + 1 到底哪个的执行时间大
            if (j < size && queue[j].nextExecutionTime > queue[j + 1].nextExecutionTime) {
                j++;
            }
            long parentTime = queue[k].nextExecutionTime;
            long childTime = queue[j].nextExecutionTime;
            if (parentTime <= childTime) {
                break;
            }
            TimerTask temp = queue[k];
            queue[k] = queue[j];
            queue[j] = temp;
            k = j;
        }
    }

    public void heapify() {
        for (int i = size / 2; i >= 1; i--) {
            fixDown(i);
        }
    }

    /**
     * 获得最早执行的任务
     * @return
     */
    public TimerTask getMin() {
        return queue[1];
    }

    /**
     * 根据下标取出任务，
     * 数组中的任务会以最小堆的形式，按照执行时间排序。
     * @param i
     * @return
     */
    public TimerTask get(int i) {
        return queue[i];
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            queue[i] = null;
        }
        size = 0;
    }
    public boolean isEmpty() {
        return size == 0;
    }
    public int size() {
        return size;
    }
}
