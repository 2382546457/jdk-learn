package org.xiaohe.主从Reator多线程;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 14:15
 */
public class Acceptor implements Runnable {
    private final ServerSocketChannel serverSocketChannel;
    /**
     * 指定从Reactor一共有16个
     */
    private static final int TOTAL_SUBREACTOR_NUM = 16;

    // 从Reactor集合
    private final List<SubReactor> subReactors = new ArrayList<>(TOTAL_SUBREACTOR_NUM);
    /**
     * 用于运行从Reactor
     */
    private final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            TOTAL_SUBREACTOR_NUM,
            TOTAL_SUBREACTOR_NUM * 2,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200)
    );
    // 初始化 Acceptor 时先将所有 SunReactor 创建好
    // 这些 SubReactor 里面的 selector 也创建好，但是这些 selector 并没有监听任何端口
    // 等建立连接的请求来了之后再将这个 socketChannel 随即绑定到一个 selector(也就是subReactor) 上。
    public Acceptor(ServerSocketChannel serverSocketChannel) throws IOException {
        this.serverSocketChannel = serverSocketChannel;
        for (int i = 0; i < TOTAL_SUBREACTOR_NUM; i++) {
            SubReactor subReactor = new SubReactor(Selector.open());
            subReactors.add(subReactor);
            threadPool.execute(subReactor);
        }
    }

    @Override
    public void run() {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);

            Optional<SubReactor> anyReactor = subReactors.stream().findAny();
            if (anyReactor.isPresent()) {
                SubReactor subReactor = anyReactor.get();
                // 由于是随机选取的 selector，这个 selector可能正在 select阻塞，现在将它唤醒，将这个链接注册在 selector 上。
                subReactor.getSelector().wakeup();
                socketChannel.register(subReactor.getSelector(), SelectionKey.OP_READ, new Handler(socketChannel));
            }
        } catch (Exception e) {

        }
    }
}
