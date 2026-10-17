package com.paytech.foundation.redis.test.infrastructure;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;

/** Child-process probe proving that the lock domain is shared across JVMs. */
public final class ResourceLockProcessProbe {

    private ResourceLockProcessProbe() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected namespace and logical key");
        }
        String password = RedisLocalEnvironmentPrecondition.requirePasswordAndVerify();
        RedissonClient client = createClient(arguments[0], password, false,
            RedisLocalEnvironmentPrecondition.PORT);
        RLock lock = client.getLock(arguments[1]);
        try {
            if (!lock.tryLock(2, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Probe could not acquire resource lock");
            }
            System.out.println("READY");
            System.out.flush();
            String command = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            if (!"RELEASE".equals(command)) {
                throw new IllegalStateException("Probe release command missing");
            }
            lock.unlock();
            System.out.println("RELEASED");
            System.out.flush();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            client.shutdown();
        }
    }

    public static RedissonClient createClient(String namespace, String password, boolean lazy, int port) {
        Config config = new Config();
        config.setThreads(2).setNettyThreads(2).setLazyInitialization(lazy)
            .setNameMapper(new TestNameMapper(namespace));
        config.useSingleServer().setAddress("redis://" + RedisLocalEnvironmentPrecondition.HOST + ":" + port)
            .setPassword(password).setConnectTimeout(500).setTimeout(500).setConnectionMinimumIdleSize(1)
            .setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2);
        return Redisson.create(config);
    }

    private record TestNameMapper(String namespace) implements NameMapper {
        @Override
        public String map(String name) {
            return namespace + ":" + name;
        }

        @Override
        public String unmap(String name) {
            String prefix = namespace + ":";
            return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
        }
    }
}
