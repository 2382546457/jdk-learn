package com.xiaohe.recycle2;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-02-10 22:03
 */
public class DefaultHandle<T> implements Handler<T> {
    /**
     * 该 handler 属于哪个 stack
     */
    private Recycler.Stack<T> stack;
    /**
     * 该 handler 拥有的对象
     */
    public T value;

    public DefaultHandle(Recycler.Stack<T> stack) {
        this.stack = stack;
    }

    @Override
    public void recycle(T object) {
        if (object != value) {
            throw new IllegalArgumentException();
        }
        this.stack.push(this);
    }
}
