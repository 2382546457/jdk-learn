package org.xiaohe.单Reator单线程;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 13:30
 */
public class Reactor implements Runnable {
    private final Selector selector;

    public Reactor(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);
        Acceptor acceptor = new Acceptor(selector, serverSocketChannel);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, acceptor);

    }
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 非阻塞获取事件
                selector.select();
                // 拿到所有事件
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    dispatch(iterator.next());
                    iterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            LockSupport.parkNanos(1000 * 1000 * 1000);
        }
    }
    private void dispatch(SelectionKey selectionKey) {
        Runnable attachment = (Runnable) selectionKey.attachment();
        if (attachment != null) {
            attachment.run();
        }
    }
}
