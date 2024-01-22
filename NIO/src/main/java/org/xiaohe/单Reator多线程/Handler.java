package org.xiaohe.单Reator多线程;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 13:47
 */
public class Handler implements Runnable {
    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            16,
            32,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
    );
    private final SocketChannel socketChannel;

    public Handler(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void run() {
        threadPoolExecutor.execute(() -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            try {
                int read = socketChannel.read(byteBuffer);
                if (read > 0) {
                    System.out.println(new String(byteBuffer.array()));
                }
            } catch (Exception e) {

            }
        });
    }
}
