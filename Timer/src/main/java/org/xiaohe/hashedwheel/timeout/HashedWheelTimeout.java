package org.xiaohe.hashedwheel.timeout;

import org.xiaohe.hashedwheel.timer.HashedWheelBucket;
import org.xiaohe.hashedwheel.timer.HashedWheelTimer;
import org.xiaohe.hashedwheel.timer.Timer;
import org.xiaohe.hashedwheel.timertask.TimerTask;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.StringUtil.simpleClassName;

/**
 * @author : 小何
 * @Description : 时间轮中每一个任务都对应一个 HashedWheelTimeout
 * @date : 2024-01-18 21:11
 */
public class HashedWheelTimeout implements Timeout {
    public static final int ST_INIT = 0;
    public static final int ST_CANCELLED = 1;
    public static final int ST_EXPIRED = 2;

    /**
     * 该任务的状态
     */
    private volatile int state = ST_INIT;
    /**
     * 原子状态修改器
     */
    private static final AtomicIntegerFieldUpdater<HashedWheelTimeout> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimeout.class, "state");

    /**
     * 该 timeout 属于哪个 timer
     */
    public final HashedWheelTimer timer;

    /**
     * 该timeout对应的 task
     */
    public final TimerTask task;

    /**
     * 该任务的执行时间
     */
    public final long deadline;

    /**
     * 该任务在时间轮中，还有几轮可以执行
     */
    public long remainingRounds;

    /**
     * 该任务在时间轮的某一个刻度里的前后任务
     */
    public HashedWheelTimeout prev;
    public HashedWheelTimeout next;

    /**
     * 该任务在哪个bucket中，也就是在哪个刻度里
     */
    public HashedWheelBucket bucket;

    public HashedWheelTimeout(HashedWheelTimer timer, TimerTask task, long deadline) {
        this.timer = timer;
        this.task = task;
        this.deadline = deadline;
    }

    @Override
    public Timer timer() {
        return timer;
    }

    @Override
    public TimerTask task() {
        return task;
    }

    @Override
    public boolean isExpired() {
        return state == ST_EXPIRED;
    }

    @Override
    public boolean isCancelled() {
        return state == ST_CANCELLED;
    }

    @Override
    public boolean cancel() {
        if (!compareAndSetState(ST_INIT, ST_CANCELLED)) {
            return false;
        }
        // 将这个任务添加到 timer的取消队列 中
        timer.cancelledTimeouts.add(this);
        return true;
    }

    public boolean compareAndSetState(int expect, int update) {
        return STATE_UPDATER.compareAndSet(this, expect, update);
    }
    public int state() {
        return state;
    }

    /**
     * 让该任务现在就执行
     */
    public void expire() {
        if (!compareAndSetState(ST_INIT, ST_EXPIRED)) {
            return;
        }

        try {
            task.run(this);
        } catch (Throwable t) {
            throw new RuntimeException("An exception was thrown by" + TimerTask.class.getSimpleName() + ".", t);
        }
    }

    /**
     * 如果这个任务已经在 bucket 中了，那就调用 bucket 的 remove方法将其移除。否则直接减就行了
     */
    public void remove() {
        HashedWheelBucket bucket = this.bucket;
        if (bucket != null) {
            bucket.remove(this);
        } else {
            timer.pendingTimeouts.decrementAndGet();
        }
    }

    @Override
    public String toString() {
        final long currentTime = System.nanoTime();
        long remaining = deadline - currentTime + timer.startTime;

        StringBuilder buf = new StringBuilder(192)
                .append(simpleClassName(this))
                .append('(')
                .append("deadline: ");
        if (remaining > 0) {
            buf.append(remaining)
                    .append(" ns later");
        } else if (remaining < 0) {
            buf.append(-remaining)
                    .append(" ns ago");
        } else {
            buf.append("now");
        }

        if (isCancelled()) {
            buf.append(", cancelled");
        }

        return buf.append(", task: ")
                .append(task())
                .append(')')
                .toString();
    }
}
