package org.xiaohe.单Reator单线程;


import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 13:41
 */
public class Handler implements Runnable {

    private final SocketChannel socketChannel;

    public Handler(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void run() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            int read = socketChannel.read(byteBuffer);
            if (read > 0) {
                System.out.println(new String(byteBuffer.array()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
