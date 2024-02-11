package com.xiaohe.recycle3;

import io.netty.util.concurrent.FastThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-02-11 15:10
 */
public abstract class Recycler<T> {
    private static final Logger logger = LoggerFactory.getLogger(Recycler.class);
    /**
     * 线程ID生成器
     */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(Integer.MIN_VALUE);

    private static final int OWN_THREAD_ID = ID_GENERATOR.getAndIncrement();
    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4 * 1024;

    /**
     * stack的最大容量
     */
    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD = DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD;

    /**
     * stack默认的初始容量
     */
    private static final int INITIAL_CAPACITY = 256;

    private static final int MAX_SHARED_CAPACITY_FACTOR = 2;
    /**
     * 每一个线程最多可以帮助多少个线程回收对象，默认为 CPU * 2
     */
    private static final int MAX_DELAYED_QUEUES_PER_THREAD = 24;

    /**
     * 每一个 Link 节点内部数组的容量，默认为16
     */
    private static final int LINK_CAPACITY = 16;

    /**
     * 对象被回收的比例。默认为8比1
     * 每创建RATIO个对象，回收其中一个，剩余对象等着被GC
     */
    private static final int RATIO = 8;
    /**
     * stack的数组的最大容量，实际上就是对象池的最大容量
     */
    private final int maxCapacityPerThread;
    private final int maxSharedCapacityFactor;
    private final int ratioMask;

    private final int maxDelayedQueuesPerThread;

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread) {
        this(maxCapacityPerThread, MAX_SHARED_CAPACITY_FACTOR);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, maxSharedCapacityFactor, RATIO, MAX_DELAYED_QUEUES_PER_THREAD);
    }
    public static int findNextPositivePowerOfTwo(final int value) {
        assert value > Integer.MIN_VALUE && value < 0x40000000;
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    public static int safeFindNextPositivePowerOfTwo(final int value) {
        return value <= 0 ? 1 : value >= 0x40000000 ? 0x40000000 : findNextPositivePowerOfTwo(value);
    }
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread) {
        ratioMask = safeFindNextPositivePowerOfTwo(ratio) - 1;
        // maxCapacityPerThread的值已经被赋成4096了，所以一定是大于0的，会走到下面的分支，给各个属性赋值
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
            this.maxSharedCapacityFactor = 1;
            this.maxDelayedQueuesPerThread = 0;
        } else {
            // 线程的stack中数组的最大容量赋值
            this.maxCapacityPerThread = maxCapacityPerThread;
            // 帮助计算每个线程可帮助原来获取对象的线程能回收的最多对象个数的辅助属性赋值
            this.maxSharedCapacityFactor = max(1, maxSharedCapacityFactor);
            // 每个线程最多可以帮助几个线程回收它们的对象赋值
            this.maxDelayedQueuesPerThread = max(0, maxDelayedQueuesPerThread);
        }
    }

    /**
     * 此对象池存放对象的Stack
     */
    private final FastThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() throws Exception {
            return new Stack<T>(Recycler.this, Thread.currentThread(), maxCapacityPerThread, maxSharedCapacityFactor, ratioMask, maxDelayedQueuesPerThread);
        }
        @Override
        protected void onRemoval(Stack<T> value) throws Exception {
            if (value.threadRef.get() == Thread.currentThread()) {
                if (DELAYED_RECYCLED.isSet()) {
                    DELAYED_RECYCLED.get().remove(value);
                }
            }
        }
    };
    /**
     * 此对象池帮助别的线程回收的对象（其实没有回收，只是暂时存放等着那个线程来拿）
     */
    private static final FastThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED = new FastThreadLocal<>() {
        @Override
        protected Object initialValue() throws Exception {
            return new WeakHashMap<Stack<?>, WeakOrderQueue>();
        }
    };
    /**
     * 当对象池中最大容量为0时，说明不让往对象池中放数据，就返回这个
     */
    private static final Handle NOOP_HANDLE = new Handle() {
        @Override
        public void recycle(Object object) {
            // NOOP
        }
    };
    public final T get() {
        if (maxCapacityPerThread == 0) {
            return newObject((Handle<T>) NOOP_HANDLE);
        }
        Stack<T> stack = threadLocal.get();
        DefaultHandle<T> handle = stack.pop();
        // 如果返回空，说明对象池中没有，并且也没有从其他线程中找回任何对象
        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle);
        }
        return (T) handle.value;
    }
    protected abstract T newObject(Handle<T> handle);

    // --------------------------------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------------------------------

    public interface Handle<T> {
        void recycle(T object);
    }
    public static final class DefaultHandle<T> implements Handle<T> {
        /**
         * 回收该对象的线程的ID
         */
        private int lastRecycledId;
        /**
         * 创建该对象的线程的ID
         */
        private int recycleId;
        /**
         * 该对象是否已经被回收
         */
        boolean hasBeenRecycled;

        /**
         * 该对象属于哪个stack
         */
        private Stack<?> stack;

        private Object value;
        public DefaultHandle(Stack<?> stack) {
            this.stack = stack;
        }

        @Override
        public void recycle(T object) {

        }
    }
    // --------------------------------------------------------------------------------------------------------
    public static final class Stack<T> {
        /**
         * stack持有本 Recycler 的引用
         */
        final Recycler<T> parent;
        /**
         * stack所属的线程
         */
        final WeakReference<Thread> threadRef;
        /**
         * 帮助其他线程回收的最大值
         */
        final AtomicInteger availableSharedCapacity;
        /**
         * 最多可以帮助多少个线程回收对象
         */
        final int maxDelayedQueues;
        /**
         * 此对象池中最多有多少对象
         */
        private final int maxCapacity;
        /**
         * 回收对象的比例
         */
        private final int ratioMask;
        private DefaultHandle<?>[] elements;
        /**
         * 此对象池中已经有多少对象了
         */
        private int size;
        /**
         * 和回收比例有关，与Ratio一起使用
         */
        private int handleRecycleCount = -1;
        /**
         * stack中有一个 WeakOrderQueue链表
         * cursor : 当前节点
         * prev : 前驱节点
         */
        private WeakOrderQueue cursor, prev;
        /**
         * 头节点
         */
        private volatile WeakOrderQueue head;
        Stack(Recycler<T> parent, Thread thread, int maxCapacity, int maxSharedCapacityFactor, int ratioMask, int maxDelayedQueues) {
            this.parent = parent;
            threadRef = new WeakReference<Thread>(thread);
            this.maxCapacity = maxCapacity;
            availableSharedCapacity = new AtomicInteger(Math.max(maxCapacity / maxSharedCapacityFactor, LINK_CAPACITY));
            elements = new DefaultHandle[Math.min(INITIAL_CAPACITY, maxCapacity)];
            this.ratioMask = ratioMask;
            this.maxDelayedQueues = maxDelayedQueues;
        }
        public synchronized void setHead(WeakOrderQueue queue) {
            queue.setNext(head);
            head = queue;
        }

        /**
         * stack数组的扩容
         * @param expectedCapacity
         * @return
         */
        int increaseCapacity(int expectedCapacity) {
            int newCapacity = elements.length;
            int maxCapacity = this.maxCapacity;
            do {
                newCapacity <<= 1;
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);
            //expectedCapacity是需要的容量，而扩容之后的容量就是所需容量的最接近的2次幂
            newCapacity = min(newCapacity, maxCapacity);
            if (newCapacity != elements.length) {
                //把数据拷贝到新的数组
                elements = Arrays.copyOf(elements, newCapacity);
            }

            return newCapacity;
        }

        /**
         * 从对象池中获取对象
         * @return
         */
        DefaultHandle<T> pop() {
            int size = this.size;
            // 如果size为0，说明对象池中没有对象. 有两种办法:
            // 1. 创建一个，创建对象的权力不在Stack中，这里返回null, Recycler会判断并创建的。
            // 2. 看看其他线程是否帮我们回收了对象，如果回收成功，就可以修改一下size
            if (size == 0) {
                if (!scavenge()) {
                    return null;
                }
                size = this.size;
            }
            size --;
            DefaultHandle ret = elements[size];
            elements[size] = null;
            if (ret.lastRecycledId != ret.recycleId) {
                throw new IllegalStateException("recycled multiple times");
            }
            ret.recycleId = 0;
            ret.lastRecycledId = 0;
            this.size = size;
            return ret;
        }

        /**
         * 从 WeakOrderQueue 中将自己的对象回收到对象池 (一次只回收一个WeakOrderQueue)
         * @return
         */
        boolean scavenge() {
            if (scavengeSome()) {
                return true;
            }
            // 如果 WeakOrderQueue 中没有Link，退出
            prev = null;
            cursor = head;
            return false;
        }
        boolean scavengeSome() {
            WeakOrderQueue prev;
            // 定义当前节点，第一次扫描时，cursor=Null
            WeakOrderQueue cursor = this.cursor;
            if (cursor == null) {
                prev = null;
                cursor = head;
                if (cursor == null) return false;
            } else {
                prev = this.prev;
            }
            boolean success = false;
            do {
                // 只要当前 WeakOrderQueue 中的对象回收了，就退出
                if (cursor.transfer(this)) {
                    success = true;
                    break;
                }
                WeakOrderQueue next = cursor.next;
                // 那个帮咱们回收对象的线程是否已经挂掉了，如果已经挂掉了，判断这个节点中是否还有对象
                if (cursor.owner.get() == null) {
                    if (cursor.hasFinalData()) {
                        for (;;) {
                            if (cursor.transfer(this)) {
                                success = true;
                            } else {
                                break;
                            }
                        }
                    }
                    if (prev != null) {
                        prev.setNext(next);
                    }
                } else {
                    prev = cursor;
                }
                cursor = next;
            } while (cursor != null && !success);
            this.prev = prev;
            this.cursor = cursor;
            return success;
        }
        boolean dropHandle(DefaultHandle<?> handle) {
            if (!handle.hasBeenRecycled) {
                if ((++handleRecycleCount & ratioMask) != 0) {
                    return true;
                }
                handle.hasBeenRecycled = true;
            }
            return false;
        }

        /**
         * 将对象放入对象池中，有两种情况:
         * 1. 该对象就是此线程申请的，放入此线程的Stack中, 对应 pushNow逻辑
         * 2. 该对象不是此线程申请的，放入 Map<stack, weakOrderQueue> 中
         * @param item
         */
        void push(DefaultHandle<?> item) {
            Thread currentThread = Thread.currentThread();
            if (threadRef.get() == currentThread) {
                pushNow(item);
            } else {
                pushLater(item, currentThread);
            }
        }


        private void pushNow(DefaultHandle<?> item) {
            // 如果是线程回收自己对象池中的对象，那么handle中的这两个属性这是还应该都还是0，是相等的。
            // 其中有一个不是0，就说明已经被回收了，或者至少被放在WeakOrderQueue中了
            if ((item.recycleId | item.lastRecycledId) != 0) {
                throw new IllegalStateException("recycled already");
            }
            // 自己回收自己的对象的时候，handle中的这两个属性都会被赋值为OWN_THREAD_ID
            item.recycleId = item.lastRecycledId = OWN_THREAD_ID;
            int size = this.size;
            // 在这里判断一下stack存储的对象的个数是否超过了最大容量，同时也检查一下回收的频率
            if (size >= maxCapacity || dropHandle(item)) {
                // 有一个不满足就不回收对象了
                return;
            }
            //走到这里说明stack允许存放的对象的个数还未到最大值，但是数组容量不够了，就把数组扩容
            if (size == elements.length) {
                // 扩容至两倍
                elements = Arrays.copyOf(elements, min(size << 1, maxCapacity));
            }
            // 对象真正被回收到stack中了
            elements[size] = item;
            // 增添了一个对象，所以size加1
            this.size = size + 1;
        }
        private void pushLater(DefaultHandle<?> item, Thread thread) {
            Map<Stack<?>, WeakOrderQueue> delayedRecycled = DELAYED_RECYCLED.get();
            WeakOrderQueue queue = delayedRecycled.get(this);
            // 如果之前没有帮这个线程回收过，有两个选择: 创建一个key-value帮他回收、拒绝帮助
            if (queue == null) {
                if (delayedRecycled.size() >= maxDelayedQueues) {
                    delayedRecycled.put(this, WeakOrderQueue.DUMMY);
                    return;
                }
                // 如果申请失败
                if ((queue = WeakOrderQueue.allocate(this, thread)) == null) {
                    return;
                }
                delayedRecycled.put(this, queue);
            } else if (queue == WeakOrderQueue.DUMMY) {
                return;
            }
            queue.add(item);
        }
        DefaultHandle<T> newHandle() {
            return new DefaultHandle<T>(this);
        }
    }
    // --------------------------------------------------------------------------------------------------------
    public static final class WeakOrderQueue {
        /**
         * 线程帮助的其他线程有限，超过了就使用它代替
         */
        static final WeakOrderQueue DUMMY = new WeakOrderQueue();

        /**
         * 假如此线程是 thread0, 那么 thread0 内部的 WeakOrderQueue如下所示:
         * thread1:  WeakOrderQueue [ head(link) -> link -> link -> tail ], owner = thread1
         *                |
         *                | next
         *                |
         * thread2:  WeakOrderQueue [ head(link) -> link -> link -> tail ], owner = thread2
         *                |
         *                | next
         *                |
         * thread3:  WeakOrderQueue [ head(link) -> link -> link -> tail ], owner = thread3
         */
        private final Head head;
        private Link tail;
        private WeakOrderQueue next;
        /**
         * 线程2帮助线程0回收了对象，线程0中的 WeakOrderQueue 的 owner 就是线程2
         */
        private final WeakReference<Thread> owner;
        private final int id = ID_GENERATOR.getAndIncrement();
        private WeakOrderQueue() {
            owner = null;
            head = new Head(null);
        }
        private WeakOrderQueue(Stack<?> stack, Thread thread) {
            tail = new Link();
            head = new Head(stack.availableSharedCapacity);
            head.link = tail;
            owner = new WeakReference<>(thread);
        }
        static WeakOrderQueue newQueue(Stack<?> stack, Thread thread) {
            // 新建一个 WeakOrderQueue
            final WeakOrderQueue queue = new WeakOrderQueue(stack, thread);
            // 头插法插入 WeakOrderQueue 中
            stack.setHead(queue);
            return queue;
        }

        /**
         * 把 WeakOrderQueue 挂在链表后面
         * @param next
         */
        private void setNext(WeakOrderQueue next) {
            assert next != this;
            this.next = next;
        }

        /**
         * 给某个线程分配一个 WeakOrderQueue，如果还有名额就分配，没有名额返回null
         * @param stack
         * @param thread
         * @return
         */
        static WeakOrderQueue allocate(Stack<?> stack, Thread thread) {
            return Head.reserveSpace(stack.availableSharedCapacity, LINK_CAPACITY) ? newQueue(stack, thread) : null;
        }

        /**
         * 将一个DefaultHandle添加到本 WeakOrderQueue 中，默认添加到最后一个Link节点里
         * 线程的WeakOrderQueue中存放的是别的线程帮自己回收的对象，别的线程将这个对象放入Map中后，会将这个对象也添加到本线程的WeakOrderQueue中
         * @param handle
         */
        void add(DefaultHandle<?> handle) {
            handle.lastRecycledId = id;
            Link tail = this.tail;
            int writeIndex;
            // 如果当前Link节点已经满了，就要创建新的节点
            if ((writeIndex = tail.get()) == LINK_CAPACITY) {
                // 如果现在也不能创建新的Link节点了，就直接返回
                if (!head.reserveSpace(LINK_CAPACITY)) {
                    return;
                }
                this.tail = tail = tail.next = new Link();
                writeIndex = tail.get();
            }
            tail.elements[writeIndex] = handle;
            // TODO 将这个对象的 stack 置为空
            handle.stack = null;
            tail.lazySet(writeIndex + 1);
        }

        /**
         * 判断 WeakOrderQueue 中是否还有未被回收的对象
         * @return
         */
        boolean hasFinalData() {
            return tail.readIndex != tail.get();
        }

        /**
         * 一个线程的 WeakOrderQueue 中存放的都是其他线程帮此线程保存的数据，现在要拿回来放在 stack 中
         * @param dst
         * @return
         */
        boolean transfer(Stack<?> dst) {
            Link head = this.head.link;
            if (head == null) {
                return false;
            }
            // 如果读到了本Link的末尾
            if (head.readIndex == LINK_CAPACITY) {
                // 没有next了，说明已经回收完了
                if (head.next == null) {
                    return false;
                }
                // 有next,将head里面之前的link丢弃，拿到next
                this.head.link = head = head.next;
                // 将这个Link占有的回收名额归还
                this.head.reclaimSpace(LINK_CAPACITY);
            }
            // 开始转移
            final int srcStart = head.readIndex;
            int srcEnd = head.get();
            final int srcSize = srcEnd - srcStart;
            if (srcSize == 0) {
                return false;
            }
            // 此线程的stack中原有的size加上被其他对象暂时拥有的size = 最终的size
            final int dstSize = dst.size;
            final int expectedCapacity = dstSize + srcSize;
            // 可能需要扩容
            if (expectedCapacity > dst.elements.length) {
                final int actualCapacity = dst.increaseCapacity(expectedCapacity);
                srcEnd = Math.min(srcStart + actualCapacity - dstSize, srcEnd);
            }
            if (srcStart != srcEnd) {
                final DefaultHandle[] srcElems = head.elements;
                final DefaultHandle[] dstElems = dst.elements;
                int newDstSize = dstSize;
                for (int i = srcStart; i < srcEnd; i++) {
                    // 从 WeakOrderQueue的Link中取出一个 Handle
                    DefaultHandle element = srcElems[i];
                    // 不允许重复回收，如果重复回收就报错
                    if (element.recycleId == 0) {
                        element.recycleId = element.lastRecycledId;
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    srcElems[i] = null;
                    // ratio变量规定了对象回收的比例，现在要判断这个对象要不要回收
                    if (dst.dropHandle(element)) {
                        continue;
                    }
                    element.stack = dst;
                    dstElems[newDstSize ++] = element;
                }
                if (srcEnd == LINK_CAPACITY && head.next != null) {
                    this.head.reclaimSpace(LINK_CAPACITY);
                    this.head.link = head.next;
                }
                head.readIndex = srcEnd;
                // 如果转移后的容量跟原来的容量一样，说明没有转移
                if (dst.size == newDstSize) {
                    return false;
                }
                dst.size = newDstSize;
                return true;
            } else  {
                return false;
            }


        }

        static final class Link extends AtomicInteger {
            /**
             * 该 Link 节点中持有的对象
             */
            private final DefaultHandle<?>[] elements = new DefaultHandle[LINK_CAPACITY];
            private int readIndex;
            /**
             * 指向下一个 Link 节点
             */
            Link next;
        }
        static final class Head {
            /**
             * 其他线程可以帮本线程回收对象的最大个数，一个 Link 节点占用 16
             */
            private final AtomicInteger availableSharedCapacity;
            Link link;
            Head(AtomicInteger availableSharedCapacity) {
                this.availableSharedCapacity = availableSharedCapacity;
            }

            /**
             * 当一个 Link 节点被垃圾回收时，要给 availableSharedCapacity 加16
             * In the future when we move to Java9+ we should use java.lang.ref.Cleaner.
             * @throws Throwable
             */
            @Override
            protected void finalize() throws Throwable {
                try {
                    super.finalize();
                } finally {
                    Link head = link;
                    link = null;
                    while (head != null) {
                        reclaimSpace(LINK_CAPACITY);
                        Link next = head.next;
                        head.next = null;
                        head = next;
                    }
                }
            }
            void reclaimSpace(int space) {
                assert space >= 0;
                availableSharedCapacity.addAndGet(space);
            }

            /**
             * 将 availableSharedCapacity 减少 space
             * @param space
             * @return
             */
            boolean reserveSpace(int space) {
                return reserveSpace(availableSharedCapacity, space);
            }
            static boolean reserveSpace(AtomicInteger availableSharedCapacity, int space) {
                assert space >= 0;
                for (;;) {
                    int available = availableSharedCapacity.get();
                    if (available < space) {
                        return false;
                    }
                    if (availableSharedCapacity.compareAndSet(available, available - space)) {
                        return true;
                    }
                }
            }
        }
    }
}
