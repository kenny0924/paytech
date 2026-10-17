package com.paytech.gateway.auto.web;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.*;

import io.netty.buffer.PooledByteBufAllocator;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/** 可重放请求体 Owner 的无内容生命周期契约测试。 */
class ReplayableRequestBodyOwnerAutoTest {
    private static final String OWNER_CLASS = "com.paytech.gateway.web.ReplayableRequestBodyOwner";

    @Test
    void test_eachSubscriptionReceivesFreshWrapperOverOneOwnerAllocation() throws Exception {
        Object owner = newOwner("replayable-body".getBytes(StandardCharsets.UTF_8));

        List<DataBuffer> first = replay(owner).collectList().block();
        List<DataBuffer> second = replay(owner).collectList().block();

        Assertions.assertNotNull(first);
        Assertions.assertNotNull(second);
        Assertions.assertAll(() -> Assertions.assertEquals(1, first.size()),
            () -> Assertions.assertEquals(1, second.size()),
            () -> Assertions.assertNotSame(first.getFirst(), second.getFirst()),
            () -> Assertions.assertEquals(15, first.getFirst().readableByteCount()),
            () -> Assertions.assertEquals(15, second.getFirst().readableByteCount()),
            () -> Assertions.assertFalse(isZeroed(owner)), () -> Assertions.assertEquals(0, clearCount(owner)));
        DataBufferUtils.release(first.getFirst());
        DataBufferUtils.release(second.getFirst());
    }

    @Test
    void test_cleanupZeroesBestEffortExactlyOnceAndRetainsFirstTerminalSignal() throws Exception {
        byte[] allocation = "temporary-owner".getBytes(StandardCharsets.UTF_8);
        Object owner = newOwner(allocation);

        clear(owner, SignalType.ON_COMPLETE);
        clear(owner, SignalType.ON_ERROR);
        clear(owner, SignalType.CANCEL);

        Assertions.assertAll(() -> Assertions.assertTrue(isZeroed(owner)),
            () -> Assertions.assertEquals(1, clearCount(owner)),
            () -> Assertions.assertEquals(SignalType.ON_COMPLETE.toString(), terminalSignal(owner)),
            () -> Assertions.assertTrue(allZero(allocation), "Owner 清理后临时数组仍存在非零字节"));
    }

    @Test
    void test_wrapperCreatedBeforeEmissionIsReleasedWhenSubscriberCancels() throws Exception {
        RecordingDataBufferFactory factory = new RecordingDataBufferFactory();

        replay(newOwner(new byte[] {1}), factory).subscribe(new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                cancel();
            }
        });

        DataBuffer wrapped = factory.lastWrapped.get();
        Assertions.assertAll(() -> Assertions.assertInstanceOf(PooledDataBuffer.class, wrapped),
            () -> Assertions.assertFalse(((PooledDataBuffer)wrapped).isAllocated()));
    }

    @Test
    void test_nullAllocationIsRejectedAtOwnershipBoundary() throws Exception {
        InvocationTargetException exception =
            Assertions.assertThrows(InvocationTargetException.class, () -> newOwner(null));

        Assertions.assertInstanceOf(NullPointerException.class, exception.getCause());
    }

    private Object newOwner(byte[] body) throws Exception {
        Class<?> type = Class.forName(OWNER_CLASS);
        Constructor<?> constructor = type.getDeclaredConstructor(byte[].class);
        constructor.setAccessible(true);
        return constructor.newInstance((Object)body);
    }

    @SuppressWarnings("unchecked")
    private Flux<DataBuffer> replay(Object owner) throws Exception {
        return replay(owner, DefaultDataBufferFactory.sharedInstance);
    }

    @SuppressWarnings("unchecked")
    private Flux<DataBuffer> replay(Object owner, DataBufferFactory factory) throws Exception {
        Method method =
            owner.getClass().getDeclaredMethod("replay", org.springframework.core.io.buffer.DataBufferFactory.class);
        method.setAccessible(true);
        return (Flux<DataBuffer>)method.invoke(owner, factory);
    }

    private void clear(Object owner, SignalType signalType) throws Exception {
        Method method = owner.getClass().getDeclaredMethod("clear", SignalType.class);
        method.setAccessible(true);
        method.invoke(owner, signalType);
    }

    private boolean isZeroed(Object owner) throws Exception {
        return (Boolean)invokeMetadata(owner, "isZeroed");
    }

    private int clearCount(Object owner) throws Exception {
        return (Integer)invokeMetadata(owner, "clearCount");
    }

    private String terminalSignal(Object owner) throws Exception {
        Object signal = invokeMetadata(owner, "terminalSignal");
        return signal == null ? null : signal.toString();
    }

    private Object invokeMetadata(Object owner, String methodName) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(owner);
    }

    private boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static final class RecordingDataBufferFactory implements DataBufferFactory {
        private final NettyDataBufferFactory delegate = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        private final AtomicReference<DataBuffer> lastWrapped = new AtomicReference<>();

        @Override
        public DataBuffer allocateBuffer() {
            return delegate.allocateBuffer();
        }

        @Override
        public DataBuffer allocateBuffer(int initialCapacity) {
            return delegate.allocateBuffer(initialCapacity);
        }

        @Override
        public DataBuffer wrap(ByteBuffer byteBuffer) {
            return delegate.wrap(byteBuffer);
        }

        @Override
        public DataBuffer wrap(byte[] bytes) {
            DataBuffer wrapped = delegate.wrap(bytes);
            lastWrapped.set(wrapped);
            return wrapped;
        }

        @Override
        public DataBuffer join(List<? extends DataBuffer> dataBuffers) {
            return delegate.join(dataBuffers);
        }

        @Override
        public boolean isDirect() {
            return delegate.isDirect();
        }
    }
}
