package org.xiaohe.单Reator多线程;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 13:47
 */
public class Acceptor implements Runnable {
    /**
     * 这个 selector 跟 reactor 用的是同一个，所以它既监听连接事件，又监听已建立的链接的读事件
     */
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    public Acceptor(Selector selector, ServerSocketChannel serverSocketChannel) {
        this.selector = selector;
        this.serverSocketChannel = serverSocketChannel;
    }

    @Override
    public void run() {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ, new Handler(socketChannel));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
