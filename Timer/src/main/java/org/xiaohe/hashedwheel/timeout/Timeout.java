package org.xiaohe.hashedwheel.timeout;

import org.xiaohe.hashedwheel.timer.Timer;
import org.xiaohe.hashedwheel.timertask.TimerTask;

/**
 * @author : 小何
 * @Description : 一个 Timeout 对应一个 TimerTask，可以查看这个任务的状态
 * @date : 2024-01-18 21:06
 */
public interface Timeout {
    /**
     * 获取这个 timeout 所属的 timer
     * @return
     */
    Timer timer();

    /**
     * 获取任务
     * @return
     */
    TimerTask task();

    /**
     * 该任务是否过期
     * @return
     */
    boolean isExpired();

    /**
     * 该任务是否取消
     * @return
     */
    boolean isCancelled();

    /**
     * 取消该任务
     * @return
     */
    boolean cancel();

}
