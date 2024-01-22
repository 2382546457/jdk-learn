package org.xiaohe.单Reator单线程;

import java.io.IOException;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-01-22 13:29
 */
public class Main {
    public static void main(String[] args) throws IOException {
        Reactor reator = new Reactor(8080);
        new Thread(reator).start();
    }
}