package org.xiaohe.主从Reator多线程;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 14:13
 */
public class SubReactor implements Runnable {
    private final Selector selector;

    public SubReactor(Selector selector) {
        this.selector = selector;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    dispatch(iterator.next());
                    iterator.remove();
                }
             } catch (Exception e) {

            }
        }
    }

    private void dispatch(SelectionKey next) {
        Runnable attachment = (Runnable) next.attachment();
        if (attachment != null) {
            attachment.run();
        }
    }

    public Selector getSelector() {
        return selector;
    }
}
