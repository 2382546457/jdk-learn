package org.xiaohe.hashedwheel.timer;

import org.xiaohe.hashedwheel.timeout.HashedWheelTimeout;
import org.xiaohe.hashedwheel.timeout.Timeout;

import java.util.Set;

/**
 * @author : 小何
 * @Description : 每一个 bucket 是时间轮中的一个刻度，并且拥有一条链表
 * @date : 2024-01-18 21:19
 */
public class HashedWheelBucket {
    public HashedWheelTimeout head;
    public HashedWheelTimeout tail;


    /**
     * 添加一个 Timeout 到链表尾部
     * @param timeout
     */
    public void addTimeout(HashedWheelTimeout timeout) {
        // 必须保证这个任务从没有被分配给任何刻度
        assert timeout.bucket == null;

        timeout.bucket = this;
        if (head == null) {
            head = tail = timeout;
        } else {
            tail.next = timeout;
            timeout.prev = tail;
            tail = timeout;
        }
    }

    /**
     * 将这个 bucket 中，执行时间在 deadline 前的所有任务都执行了
     * @param deadline
     */
    public void expireTimeouts(long deadline) {
        HashedWheelTimeout timeout = head;

        while (timeout != null) {
            HashedWheelTimeout next = timeout.next;
            // 如果圈数小于等于0，说明这一圈就可以执行，但是如果还没有到时间
            // 比如一个 bucket 为 1s，deadline为 1.563s
            // 1. timeout.deadline 为 1.500s，则可以执行
            // 2. timeout.deadline 为 1.7s。那么这个timeout就不用执行。执行if逻辑外的 timeout = next，然后进入下一个循环
            if (timeout.remainingRounds <= 0) {
                next = remove(timeout);
                if (timeout.deadline <= deadline) {
                    timeout.expire();
                } else {
                    throw new IllegalStateException(String.format("timeout.deadline (%d) > deadline (%d)", timeout.deadline, deadline));
                }
            } else if (timeout.isCancelled()) {
                // 如果该任务的状态为已取消，将其remove
                next = remove(timeout);
            } else {
                timeout.remainingRounds--;
            }
            timeout = next;
        }
    }
    public HashedWheelTimeout remove(HashedWheelTimeout timeout) {
        HashedWheelTimeout next = timeout.next;
        // 由于是双向链表，所以删除的逻辑比较复杂
        if (timeout.prev != null) {
            timeout.prev.next = next;
        }
        if (timeout.next != null) {
            timeout.next.prev = timeout.prev;
        }

        if (timeout == head) {
            if (timeout == tail) {
                tail = null;
                head = null;
            } else {
                head = next;
            }
        } else if (timeout == tail) {
            tail = timeout.prev;
        }
        timeout.prev = null;
        timeout.next = null;
        timeout.bucket = null;
        // 删除了一个 timeout, 数量减一
        timeout.timer.pendingTimeouts.decrementAndGet();
        return next;
    }

    /**
     * 将这个 bucket 中的所有 timeout 全部删除，未执行、未取消的 timeout 放入 set 中
     * @param set
     */
    public void clearTimeouts(Set<Timeout> set) {
        for (;;) {
            HashedWheelTimeout timeout = pollTimeout();
            if (timeout == null) {
                return;
            }
            if (timeout.isExpired() || timeout.isCancelled()) {
                continue;
            }
            set.add(timeout);
        }
    }

    /**
     * 取出并删除链表的头节点
     * @return
     */
    private HashedWheelTimeout pollTimeout() {
        HashedWheelTimeout head = this.head;
        if (head == null) {
            return null;
        }
        HashedWheelTimeout next = head.next;
        if (next == null) {
            tail = this.head = null;
        } else {
            this.head = next;
            next.prev = null;
        }

        head.next = null;
        head.prev = null;
        head.bucket = null;
        return head;
    }
}
