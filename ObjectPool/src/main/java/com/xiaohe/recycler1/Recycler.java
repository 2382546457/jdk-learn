package com.xiaohe.recycler1;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-02-10 21:45
 */
public abstract class Recycler<T> {
    /**
     * 存放对象的数组
     */
    private final Object[] elements;
    private int INITIAL_CAPACITY = 256;
    private int size;

    public Recycler() {
        this.elements = new Object[INITIAL_CAPACITY];
    }
    public T pop() {
        synchronized (elements) {
            if (size == 0) {
                return newObject();
            }
            T t = (T)elements[--size];
            elements[size] = null;
            return t;
        }
    }
    protected abstract T newObject();

    public void push(T object) {
        synchronized (elements) {
            elements[size++] = object;
        }
    }
}
