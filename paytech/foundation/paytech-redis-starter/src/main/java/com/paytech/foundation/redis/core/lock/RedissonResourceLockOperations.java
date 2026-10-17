package com.paytech.foundation.redis.core.lock;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import com.paytech.foundation.redis.core.autoconfigure.RedisNamespaceProperties;

/** 通过现有服务 Namespace 下的 Redisson Client 执行资源锁操作。 */
public final class RedissonResourceLockOperations implements ResourceLockOperations {

    private final ObjectProvider<RedissonClient> clientProvider;
    private final RedisNamespaceProperties namespaceProperties;
    private final Environment environment;
    private volatile RedissonClient validatedClient;

    public RedissonResourceLockOperations(ObjectProvider<RedissonClient> clientProvider) {
        this(clientProvider, null, null);
    }

    public RedissonResourceLockOperations(ObjectProvider<RedissonClient> clientProvider,
        RedisNamespaceProperties namespaceProperties, Environment environment) {
        this.clientProvider = clientProvider;
        this.namespaceProperties = namespaceProperties;
        this.environment = environment;
    }

    @Override
    public LockHandle lock(String logicalKey) {
        RedissonClient client = validatedClient;
        if (client == null) {
            client = clientProvider.getIfUnique();
        }
        if (client == null) {
            throw new IllegalStateException("Resource Lock requires one RedissonClient");
        }
        validateNameMapping(client, logicalKey);
        RLock lock = client.getLock(logicalKey);
        return new LockHandle() {
            @Override
            public boolean tryLock(long waitNanos, long leaseNanos) throws InterruptedException {
                return lock.tryLock(waitNanos, leaseNanos, NANOSECONDS);
            }

            @Override
            public boolean isHeldByCurrentThread() {
                return lock.isHeldByCurrentThread();
            }

            @Override
            public void unlock() {
                lock.unlock();
            }
        };
    }

    void bindValidatedClient(RedissonClient client) {
        if (client == null) {
            throw new IllegalArgumentException("validated RedissonClient is required");
        }
        RedissonClient current = validatedClient;
        if (current != null && current != client) {
            throw new IllegalStateException("Resource Lock RedissonClient identity is already frozen");
        }
        validatedClient = client;
    }

    private void validateNameMapping(RedissonClient client, String logicalKey) {
        if (namespaceProperties == null || environment == null) {
            return;
        }
        String keyPrefix = namespaceProperties.resolveKeyPrefix(environment.getProperty("spring.application.name"));
        try {
            if (!hasExpectedNameMapping(client, keyPrefix, logicalKey)) {
                throw new IllegalStateException(
                    "RedissonClient NameMapper does not apply the configured service namespace");
            }
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                "RedissonClient NameMapper does not apply the configured service namespace", failure);
        }
    }

    static boolean hasExpectedNameMapping(RedissonClient client, String keyPrefix, String logicalKey) {
        var config = client.getConfig();
        var nameMapping = config == null ? null : config.getNameMapper();
        String mapped = nameMapping == null ? null : nameMapping.map(logicalKey);
        return (keyPrefix + ":" + logicalKey).equals(mapped) && logicalKey.equals(nameMapping.unmap(mapped));
    }
}
