package org.xiaohe.hashedwheel.timer;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import org.xiaohe.hashedwheel.timeout.HashedWheelTimeout;
import org.xiaohe.hashedwheel.timeout.Timeout;
import org.xiaohe.hashedwheel.timertask.TimerTask;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-18 21:15
 */
public class HashedWheelTimer implements Timer {
    /**
     * 刚添加进来的任务
     */
    public final Queue<HashedWheelTimeout> timeouts = PlatformDependent.newMpscQueue();
    /**
     * 状态被设置为已取消的任务
     */
    public Queue<HashedWheelTimeout> cancelledTimeouts = PlatformDependent.newMpscQueue();

    /**
     * timeout个数
     */
    public final AtomicLong pendingTimeouts = new AtomicLong(0);

    private final long maxPendingTimeouts;
    /**
     * startTime 只能初始化一次
     */
    public final CountDownLatch startTimeInitialized = new CountDownLatch(1);
    /**
     * 这个Timer的启动时间
     */
    public volatile long startTime;

    /**
     * worker线程的状态
     */
    private volatile int workerState;

    public static final int WORKER_STATE_INIT = 0;
    public static final int WORKER_STATE_STARTED = 1;
    public static final int WORKER_STATE_SHUTDOWN = 2;
    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> WORKER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer.class, "workerState");


    public final long tickDuration;
    public final HashedWheelBucket[] wheel;
    public final int mask;


    /**
     *
     * @param threadFactory
     * @param tickDuration 一个轮子有多长时间，默认200ms
     * @param unit
     * @param ticksPerWheel 时间轮有多少个轮，默认512
     * @param leakDetection
     * @param maxPendingTimeouts
     */
    public HashedWheelTimer(ThreadFactory threadFactory,
                            long tickDuration,
                            TimeUnit unit,
                            int ticksPerWheel,
                            boolean leakDetection,
                            long maxPendingTimeouts) {
        ObjectUtil.checkNotNull(threadFactory, "threadFactory");
        ObjectUtil.checkNotNull(unit, "unit");
        ObjectUtil.checkPositive(tickDuration, "tickDuration");
        ObjectUtil.checkPositive(ticksPerWheel, "ticksPerWheel");

        wheel = null;
        mask = wheel.length - 1;

        this.tickDuration = unit.toNanos(tickDuration);
        this.maxPendingTimeouts = maxPendingTimeouts;
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, Timeout unit) {
        return null;
    }

    @Override
    public Set<Timeout> stop() {
        return null;
    }


    public void start() {
        // 如果 startTime 没有初始化，等待它初始化
        while (startTime == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }





    // -----------------------------------------------------------------------------------------
    /**
     * 工作线程需要做的事
     */
    public class Worker implements Runnable {

        /**
         * worker线程结束时，将所有没执行的任务放进去。方便打印日志
         */
        public final Set<Timeout> unprocessedTimeouts = new HashSet<>();
        /**
         * 指针移动次数
         */
        public long tick;
        @Override
        public void run() {
            // 给 starterTime 赋值
            startTime = System.nanoTime();
            if (startTime == 0) {
                startTime = 1;
            }
            // 通知
            startTimeInitialized.countDown();

            // 如果worker线程的状态一直是 started，就一直循环
            do {
                // 一般情况下，deadline >= tickDuration
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    // 获取此时所处的轮子，也就是第一个轮子。
                    int idx = (int) (tick & mask);
                    // 删除取消的任务
                    processCancelledTasks();
                    HashedWheelBucket bucket = wheel[idx];
                    // 将timeouts转移到buckets中
                    transferTimeoutsToBuckets();
                    bucket.expireTimeouts(deadline);
                    // 走一步
                    tick++;
                }
            } while (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_STARTED);

            // 走出循环了，说明线程要结束了
            for (HashedWheelBucket bucket : wheel) {
                bucket.clearTimeouts(unprocessedTimeouts);
            }
            for (;;) {
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (!timeout.isCancelled()) {
                    unprocessedTimeouts.add(timeout);
                }
            }
            processCancelledTasks();
        }

        public long waitForNextTick() {
            // 刚开始的时候 tick = 0, tickDuration = 100ms
            // 所以 deadline = 100ms
            // 下一次调用这个方法，deadline 就会变成 200ms、300ms、400ms...
            long deadline = tickDuration * (tick + 1);
            for (;;) {
                final long currentTime = System.nanoTime() - startTime;
                long sleepTimeMs = (deadline - currentTime + 999999)  / 1000000;
                if (sleepTimeMs <= 0) {
                    if (currentTime == Long.MIN_VALUE) {
                        return -Long.MAX_VALUE;
                    } else {
                        // 返回当前时间
                        return currentTime;
                    }
                }

                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException ignored) {
                    if (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_SHUTDOWN) {
                        return Long.MIN_VALUE;
                    }
                }
            }
        }

        /**
         * 将 cancelledTimeouts 中的任务删除
         */
        public void processCancelledTasks() {
            for (;;) {
                HashedWheelTimeout timeout = cancelledTimeouts.poll();
                if (timeout == null) {
                    break;
                }
                try {
                    timeout.remove();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
        public void transferTimeoutsToBuckets() {
            // 一次最多转移 100000 个任务
            for (int i = 0; i < 100000; i++) {
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    break;
                }
                long calculated = timeout.deadline / tickDuration;
                timeout.remainingRounds = (calculated - tick) / wheel.length;

                final long ticks = Math.max(calculated, tick);
                int stopIndex = (int) (ticks & mask);

                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
            }
        }
    }
}
