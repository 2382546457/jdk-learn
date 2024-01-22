package org.xiaohe.主从Reator多线程;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 14:16
 */
public class Handler implements Runnable {

    private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(16, 32,
            60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200));

    private final SocketChannel clientSocketChannel;

    public Handler(SocketChannel clientSocketChannel) {
        this.clientSocketChannel = clientSocketChannel;
    }

    @Override
    public void run() {
        threadPool.execute(() -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            try {
                // 读取数据
                int read = clientSocketChannel.read(byteBuffer);
                if (read > 0) {
                    System.out.println(new String(byteBuffer.array()));
                }
                // 睡眠10S，演示任务执行耗时长也不会阻塞处理其它客户端请求
                LockSupport.parkNanos(1000 * 1000 * 1000 * 10L);
            } catch (IOException e1) {
            }
        });
    }

}
