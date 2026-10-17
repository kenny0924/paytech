package com.paytech.foundation.redis.test.fake;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;

/**
 * 通过真实 Redisson 接口驱动内存锁探针，确保 Spring 集成测试仍经过 Foundation 的生产执行链。
 */
public final class InMemoryRedissonClient {

    private final InMemoryResourceLockOperations operations;
    private final RedissonClient client;

    public InMemoryRedissonClient(String namespace) {
        this(namespace, new InMemoryResourceLockOperations());
    }

    public InMemoryRedissonClient(String namespace, List<String> events) {
        this(namespace, new InMemoryResourceLockOperations(events));
    }

    private InMemoryRedissonClient(String namespace, InMemoryResourceLockOperations operations) {
        this.operations = operations;
        String keyPrefix = namespace + ":";
        Config config = new Config().setNameMapper(new NameMapper() {
            @Override
            public String map(String name) {
                return keyPrefix + name;
            }

            @Override
            public String unmap(String name) {
                return name.startsWith(keyPrefix) ? name.substring(keyPrefix.length()) : name;
            }
        });
        client = (RedissonClient)Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
            new Class<?>[] {RedissonClient.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getConfig" -> config;
                case "getLock" -> lock(String.valueOf(arguments[0]));
                case "shutdown" -> null;
                case "isShutdown", "isShuttingDown" -> false;
                case "toString" -> "in-memory-redisson-client";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException("unsupported RedissonClient method: "
                    + method.toGenericString());
            });
    }

    public RedissonClient client() {
        return client;
    }

    public InMemoryResourceLockOperations operations() {
        return operations;
    }

    private RLock lock(String logicalKey) {
        var handle = operations.lock(logicalKey);
        return (RLock)Proxy.newProxyInstance(RLock.class.getClassLoader(), new Class<?>[] {RLock.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "tryLock" -> tryLock(handle, arguments);
                case "isHeldByCurrentThread" -> handle.isHeldByCurrentThread();
                case "unlock" -> {
                    handle.unlock();
                    yield null;
                }
                case "getName" -> logicalKey;
                case "toString" -> "in-memory-lock:" + logicalKey;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException("unsupported RLock method: "
                    + method.toGenericString());
            });
    }

    private boolean tryLock(InMemoryResourceLockOperations.LockHandle handle, Object[] arguments)
        throws InterruptedException {
        if (arguments == null || arguments.length != 3 || !(arguments[2] instanceof TimeUnit unit)) {
            throw new UnsupportedOperationException("only timed lease tryLock is supported");
        }
        long waitNanos = unit.toNanos(((Number)arguments[0]).longValue());
        long leaseNanos = unit.toNanos(((Number)arguments[1]).longValue());
        return handle.tryLock(waitNanos, leaseNanos);
    }
}
