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
import java.util.concurrent.RejectedExecutionException;
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
    public final Worker worker = new Worker();
    public final Thread workerThread;


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
        // 省略判断条件，都是一些判空啥的。
        wheel = createWheel(ticksPerWheel);
        mask = wheel.length - 1;
        this.tickDuration = unit.toNanos(tickDuration);
        this.maxPendingTimeouts = maxPendingTimeouts;
        workerThread = threadFactory.newThread(worker);
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        // 判空代码不写了
        long pendingTimeoutsCount = pendingTimeouts.incrementAndGet();
        if (maxPendingTimeouts > 0 && pendingTimeoutsCount > maxPendingTimeouts) {
            pendingTimeouts.decrementAndGet();
            throw new RejectedExecutionException();
        }
        start();
        long deadline = System.nanoTime() + unit.toNanos(delay) - startTime;
        // 说明这个任务的执行时间已经过去
        if (delay > 0 && deadline < 0) {
            deadline = Long.MAX_VALUE;
        }
        HashedWheelTimeout timeout = new HashedWheelTimeout(this, task, deadline);
        timeouts.add(timeout);
        return timeout;
    }

    @Override
    public Set<Timeout> stop() {
        return null;
    }


    /**
     * 启动工作线程
     */
    public void start() {
        switch (WORKER_STATE_UPDATER.get(this)) {
            case WORKER_STATE_INIT:
                if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                    workerThread.start();
                }
                break;
            case WORKER_STATE_STARTED:
                break;
            case WORKER_STATE_SHUTDOWN:
                throw new IllegalStateException();
            default:
                throw new Error("Invalid WorkerState");
        }
        // 如果 startTime 没有初始化，等待它初始化
        while (startTime == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static HashedWheelBucket[] createWheel(int ticksPerWheel) {
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException(
                    "ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }
        if (ticksPerWheel > 1073741824) {
            throw new IllegalArgumentException(
                    "ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
        }
        // 将ticksPerWheel转为 2^n，要跟它离得最近的而且大于它的。
        ticksPerWheel = normalizedTicksPerWheel(ticksPerWheel);

        HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
        for (int i = 0; i < ticksPerWheel; i++) {
            wheel[i] = new HashedWheelBucket();
        }
        return wheel;
    }

    private static int normalizedTicksPerWheel(int ticksPerWheel) {
        int normalizedTicksPerWheel = 1;
        while (normalizedTicksPerWheel < ticksPerWheel) {
            normalizedTicksPerWheel <<= 1;
        }
        return normalizedTicksPerWheel;
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
            // 换言之，deadline 是从 startTime 启动后的每一个刻度（每一个时间轮），如果返回的是deadline，那么会将执行时间在deadline前的任务都执行
            long deadline = tickDuration * (tick + 1);
            // 每一个执行waitForNextTick的时间都不一样，大致有两种情况:
            // 1. deadline对应的时间轮的任务执行耗时较短，如 返回的deadline=100ms,任务执行了30ms，那么现在就是130ms，
            //    但是还没有到达200ms，所以就要睡眠70ms，然后将 200ms返回
            // 2. deadline对应的那个轮子的任务执行时间较长，如 返回的 deadline=100ms, 任务执行了120ms，那么现在就是 220ms
            //    已经超过了200ms这个刻度，那么就不再等待，直接返回 220ms，将执行时间在220ms之前的任务都执行掉
            for (;;) {
                // 1. currentTime = 130ms, duration = 200ms
                // 2. currentTime = 220ms, duration = 200ms
                final long currentTime = System.nanoTime() - startTime;
                // 1. sleepTimeMs = 200 - 130 = 70ms
                // 2. sleepTimeMs = 200 - 220 = -20ms
                long sleepTimeMs = (deadline - currentTime + 999999)  / 1000000;
                // 1. sleepTimeMs > 0，说明现在还没有走完这一刻度，要先沉睡 sleepTimeMs
                // 2. sleepTimeMs < 0，说明现在已经超过了这一刻度，直接返回当前时间
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
                // 由于执行这个方法前已经执行过 processCancelledTasks，所以现在肯定不会出现任务为空或者任务状态未取消的情况
                if (timeout == null) {
                    break;
                }
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    break;
                }
                // 假如 timeout.deadline = 31000ms, tickDuration = 100ms, 整个时间轮一圈有60个刻度,即wheel.length=60
                // calculated = 31000 / 100 = 310，那么这个任务需要310个刻度，但是一圈只有60个刻度
                // 这个任务就应该转 5 圈之后放在第 10 个刻度中。
                // timeout.remainingRounds = 310 / 60 = 5
                long calculated = timeout.deadline / tickDuration;
                timeout.remainingRounds = (calculated - tick) / wheel.length;
                // 一般来说都是 calculated 最大，stopIndex = calculated % wheel.length = 10. 将任务放在这个刻度中
                final long ticks = Math.max(calculated, tick);
                int stopIndex = (int) (ticks & mask);

                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
            }
        }
    }
}
