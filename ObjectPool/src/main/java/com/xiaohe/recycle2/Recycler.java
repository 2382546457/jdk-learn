package com.xiaohe.recycle2;

import io.netty.util.concurrent.FastThreadLocal;

import java.util.Arrays;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-02-10 21:51
 */
public abstract class Recycler<T> {
    static final class Stack<T> {
        /**
         * stack中现有对象的数量
         */
        private int size;
        /**
         * stack的最大容量
         */
        private final int maxCapacity;
        /**
         * stack存放数据的地方
         */
        private DefaultHandle<?>[] elements;

        public DefaultHandle<T> newHandle() {
            return new DefaultHandle<>(this);
        }
        public Stack(int maxCapacity) {
            elements = new DefaultHandle[INITIAL_CAPACITY];
            this.maxCapacity = maxCapacity;
        }
        public DefaultHandle<T> pop() {
            int size = this.size;
            if (size == 0) {
                return null;
            }
            size--;
            DefaultHandle t = elements[size];
            elements[size] = null;
            this.size = size;
            return t;
        }
        public void push(DefaultHandle<?> object) {
            int size = this.size;
            // 超过了最大容量对象池就不要了
            if (size >= maxCapacity) {
                return;
            }
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, Math.min(size << 1, maxCapacity));
            }
            elements[size] = object;
            this.size = size + 1;
        }
    }
    /**
     * stack的最大容量
     */
    private static final int DEFAULT_MAX_CAPACITY_PRE_THREAD = 4 * 1024;
    /**
     * stack的默认容量
     */
    private static final int INITIAL_CAPACITY = 256;

    protected abstract T newObject(Handler<T> handle);
    private final FastThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() throws Exception {
            return new Stack<T>(DEFAULT_MAX_CAPACITY_PRE_THREAD);
        }
    };
    public final T get() {
        // 每个线程得到各自的stack
        Stack<T> stack = threadLocal.get();
        DefaultHandle<T> handle = stack.pop();

        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle);
        }
        return handle.value;
    }
    public void recycle(T object) {

    }


}
