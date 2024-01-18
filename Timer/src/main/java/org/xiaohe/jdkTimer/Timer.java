package org.xiaohe.jdkTimer;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-18 15:02
 */
public class Timer {
    /**
     * 存放任务，小根堆
     */
    private final TaskQueue queue = new TaskQueue();

    /**
     * 工作线程
     * 负责 : 扫描堆、执行任务
     */
    private final TimerThread thread = new TimerThread(queue);

    private final Object threadReaper = new Object() {
        protected void finalize() throws Throwable {
            synchronized (queue) {
                thread.newTasksMayBeScheduled = false;
                queue.notify();
            }
        }
    };
    /**
     * 给 TimerThread 起名字
     */
    private final static AtomicInteger nextSerialNumber = new AtomicInteger(0);

    /**
     * 获取 nextSerialNumber， 并将其加一
     * @return
     */
    private static int serialNumber() {
        return nextSerialNumber.getAndIncrement();
    }

    public Timer() {
        this("Timer-" + serialNumber());
    }
    public Timer(boolean isDaemon) {
        this("Timer-" + serialNumber(), isDaemon);
    }

    public Timer(String name) {
        thread.setName(name);
        thread.start();
    }

    public Timer(String name, boolean isDaemon) {
        thread.setDaemon(isDaemon);
        thread.setName(name);
        thread.start();
    }

    /**
     * 延迟任务
     * @param task
     * @param delay 延迟的时间
     */
    public void schedule(TimerTask task, long delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay 必须大于0");
        }
        sched(task, System.currentTimeMillis() + delay, 0);
    }

    /**
     * 指定时间执行
     * @param task
     * @param time 执行的时间
     */
    public void schedule(TimerTask task, Date time) {
        sched(task, time.getTime(), 0);
    }

    public void schedule(TimerTask task, long delay, long period) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay 必须大于0");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period 必须大于0");
        }
        sched(task, System.currentTimeMillis() + delay, -period);
    }

    public void schedule(TimerTask task, Date firstTime, long period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period 必须大于0");
        }
        sched(task, firstTime.getTime(), -period);
    }

    /**
     * schedule 错过了就错过了，从现在开始计时。
     * scheduleAtFixedRate 错过了也要从指定时间开始计时。
     * @param task
     * @param delay
     * @param period
     */
    public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay 必须大于0");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period 必须大于0");
        }
        sched(task, System.currentTimeMillis() + delay, period);
    }
    public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
        if (period <= 0)
            throw new IllegalArgumentException("Non-positive period.");
        sched(task, firstTime.getTime(), period);
    }
    /**
     * 最终调用的方法
     * @param task 任务
     * @param time 延迟时间
     * @param period 循环时间
     */
    private void sched(TimerTask task, long time, long period) {
        if (time < 0) {
            throw new IllegalArgumentException("Illegal execution time.");
        }
        if (Math.abs(period) > (Long.MAX_VALUE >> 1)) {
            period >>= 1;
        }
        synchronized (queue) {
            if (!thread.newTasksMayBeScheduled) {
                throw new IllegalStateException("Timer already cancelled.");
            }
            synchronized (task.lock) {
                // 如果任务的状态为已取消，则不放进去
                if (task.state != TimerTask.VIRGIN) {
                    throw new IllegalStateException(
                            "Task already scheduled or cancelled");
                }
                task.nextExecutionTime = time;
                task.period = period;
                task.state = TimerTask.SCHEDULED;
            }
            queue.add(task);
            // 如果这个任务放到了第一个，叫醒 TimerThread
            if (queue.getMin() == task) {
                queue.notify();
            }
        }
    }

    public void cancel() {
        synchronized (queue) {
            thread.newTasksMayBeScheduled = false;
            queue.clear();
            queue.notify();
        }
    }
    public int purge() {
        int result = 0;
        synchronized (queue) {
            for (int i = queue.size(); i > 0; i++) {
                if (queue.get(i).state == TimerTask.CANCELLED) {
                    queue.quickRemove(i);
                    result++;
                }
            }
            if (result != 0) {
                queue.heapify();
            }
        }
        return result;
    }
}
