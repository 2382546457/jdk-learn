package org.xiaohe.timer;

/**
 * @author : 小何
 * @Description : 工作线程
 * @date : 2024-01-18 15:03
 */
public class TimerThread extends Thread {
    /**
     * 这个标志由 Timer 中的 reaper 设置为false
     */
    public boolean newTasksMayBeScheduled = true;

    private TaskQueue queue;

    public TimerThread(TaskQueue queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            mainLoop();
        } finally {
            synchronized (queue) {
                newTasksMayBeScheduled = false;
                queue.clear();
            }
        }
    }
    private void mainLoop() {
        // 死循环
        while (true) {
            try {
                TimerTask task;
                boolean taskFired;
                // TODO 这里锁整个 queue，不明白为啥。
                synchronized (queue) {
                    // 如果堆中没有内容，阻塞
                    while (queue.isEmpty() && newTasksMayBeScheduled) {
                        // wait释放 queue 的锁
                        queue.wait();
                    }
                    // 这个线程被唤醒有两种情况:
                    // 1. 堆中有任务了
                    // 2. 被强制唤醒，线程要停止工作了，
                    if (queue.isEmpty()) {
                        break;
                    }
                    long currentTime, executionTime;
                    task = queue.getMin();
                    // 锁住这个任务的lock
                    synchronized (task.lock) {
                        // 如果这个任务之前已经被取消，从堆中删除
                        if (task.state == TimerTask.CANCELLED) {
                            queue.removeMin();
                            continue;
                        }
                        currentTime = System.currentTimeMillis();
                        executionTime = task.nextExecutionTime;
                        // 如果需要执行的时间小于当前时间，那么这个任务就能执行
                        // 还需要判断一下任务是否为定期执行，如果不是定期执行，现在就可以将其删除
                        // 如果是定期执行，还要重新计算它的下一次执行时间
                        if (taskFired = (executionTime <= currentTime)) {
                            if (task.period == 0) {
                                queue.removeMin();
                                task.state = TimerTask.EXECUTED;
                            } else {
                                queue.rescheduleMin(
                                        task.period < 0 ? currentTime - task.period : executionTime + task.period
                                );
                            }
                        }
                    }
                    // 如果没有被执行，这个线程wait一会
                    if (!taskFired) {
                        queue.wait(executionTime - currentTime);
                    }
                }
                // 如果被点燃了，就执行这个任务
                if (taskFired) {
                    task.run();
                }
            } catch (Exception e) {

            }
        }
    }
}
