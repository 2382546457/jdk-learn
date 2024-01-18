package org.xiaohe.jdkTimer;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-18 14:17
 */
public abstract class TimerTask implements Runnable {
    /**
     * 取消任务时的锁
     */
    public final Object lock = new Object();

    /**
     * 该任务的状态
     */
    public int state = VIRGIN;

    static final int VIRGIN = 0;

    static final int SCHEDULED   = 1;


    static final int EXECUTED    = 2;

    static final int CANCELLED   = 3;

    /**
     * 该任务的执行时间
     */
    long nextExecutionTime;
    /**
     * 该任务的周期
     * 如果是周期性任务，比如3s执行一次的任务，period会被赋值为3
     */
    long period;


    public TimerTask() {
    }

    /**
     * 用户需要实现的方法
     */
    public abstract void run();
    public TimerTask(long nextExecutionTime, long period) {
        this.nextExecutionTime = nextExecutionTime;
        this.period = period;
    }

    /**
     * 取消该任务。如果状态为 SCHEDULED，则取消成功
     * @return
     */
    public boolean cancel() {
        synchronized (lock) {
            boolean result = (state == SCHEDULED);
            state = CANCELLED;
            return result;
        }
    }

    /**
     * 返回此任务最近一次的计划执行时间
     * 一般情况下 period >= 0, 那么会走 nextExecutionTime - period
     * @return
     */
    public long scheduleExecutionTime() {
        synchronized (lock) {
            return (period < 0 ? nextExecutionTime + period : nextExecutionTime - period);
        }
    }
}
