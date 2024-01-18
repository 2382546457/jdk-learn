package org.xiaohe.jdkTimer;

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
                // 单线程按理来说不需要锁，但是除了这个线程，还有一个main线程要往这个queue里放任务。
                synchronized (queue) {
                    // 如果堆中没有内容，阻塞
                    while (queue.isEmpty() && newTasksMayBeScheduled) {
                        // wait释放 queue 的锁
                        queue.wait();
                    }
                    // 这个线程被唤醒有两种情况:
                    // 1. 堆中有任务了
                    // 2. 被强制唤醒，线程要停止工作了。
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
                                // 如果任务的 period 不为0. 有两种情况
                                // 1. 调用 schedule 方法，period < 0.
                                // 2. 调用 scheduleFixedRate 方法，period > 0.
                                // 它俩的区别是：schedule错过了就错过了，从现在开始计时。scheduleFixedRate错过了也要从指定时间开始计时。
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
