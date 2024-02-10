package com.xiaohe.recycle2;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-02-10 22:02
 */
public interface Handler<T> {
    public void recycle(T object);
}
