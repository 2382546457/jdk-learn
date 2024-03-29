package org.xiaohe.hashedwheel.timer;

import org.xiaohe.hashedwheel.timeout.Timeout;
import org.xiaohe.hashedwheel.timertask.TimerTask;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-18 21:06
 */
public interface Timer {
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    /**
     * 停止所有任务
     * @return
     */
    Set<Timeout> stop();
}
