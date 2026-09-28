package com.paytech.foundation.redis.test.configuration;

import java.util.ArrayList;
import java.util.List;

import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor;
import com.paytech.foundation.redis.test.fake.InMemoryRedissonClient;

@Configuration(proxyBeanMethods = false)
public class ResourceLockTestConsumerConfiguration {

    @Bean
    List<String> resourceLockEvents() {
        return new ArrayList<>();
    }

    @Bean
    InMemoryRedissonClient inMemoryRedissonClient(Environment environment,
        List<String> resourceLockEvents) {
        return new InMemoryRedissonClient(environment.getRequiredProperty("spring.application.name"),
            resourceLockEvents);
    }

    @Bean
    RedissonClient redissonClient(InMemoryRedissonClient inMemoryRedissonClient) {
        return inMemoryRedissonClient.client();
    }

    @Bean
    FoundationRedissonClientDescriptor foundationRedissonClientDescriptor(RedissonClient redissonClient,
        Environment environment) {
        return new FoundationRedissonClientDescriptor(redissonClient,
            environment.getRequiredProperty("spring.application.name"));
    }

    @Bean
    RecordingTransactionManager transactionManager(List<String> resourceLockEvents) {
        return new RecordingTransactionManager(resourceLockEvents);
    }

    @Bean
    TransactionTemplate transactionTemplate(RecordingTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    LockedCommand lockedCommand(TransactionTemplate transactionTemplate, List<String> resourceLockEvents) {
        return new LockedCommand(transactionTemplate, resourceLockEvents);
    }

    @Bean
    CommandCaller commandCaller(LockedCommand lockedCommand) {
        return new CommandCaller(lockedCommand);
    }

    public static final class CommandCaller {
        private final LockedCommand command;

        CommandCaller(LockedCommand command) {
            this.command = command;
        }

        public String invoke(boolean fail) {
            return command.execute(fail);
        }
    }

    public static class LockedCommand {
        private final TransactionTemplate transactionTemplate;
        private final List<String> events;

        LockedCommand(TransactionTemplate transactionTemplate, List<String> events) {
            this.transactionTemplate = transactionTemplate;
            this.events = events;
        }

        @ResourceLock(prefix = "gateway-configuration:catalog")
        public String execute(boolean fail) {
            return transactionTemplate.execute(status -> {
                events.add("business");
                if (fail) {
                    throw new BusinessFailure();
                }
                return "done";
            });
        }
    }

    public static final class BusinessFailure extends RuntimeException {}

    public static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events;

        RecordingTransactionManager(List<String> events) {
            this.events = events;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            events.add("transaction-begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }
    }
}
