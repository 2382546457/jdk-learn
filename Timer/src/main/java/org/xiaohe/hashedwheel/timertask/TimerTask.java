package org.xiaohe.hashedwheel.timertask;

import org.xiaohe.hashedwheel.timeout.Timeout;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-18 21:06
 */
public interface TimerTask {
    void run(Timeout timeout) throws Exception;
}
