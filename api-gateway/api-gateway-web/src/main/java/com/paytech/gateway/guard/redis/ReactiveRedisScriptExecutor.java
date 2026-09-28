package com.paytech.gateway.guard.redis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;

import io.lettuce.core.RedisException;
import reactor.core.publisher.Mono;

/** 低层 Reactive Redis Lua 执行器，统一连接生命周期和失败语义。 */
@Component
public final class ReactiveRedisScriptExecutor {
    private final ReactiveRedisConnectionFactory connectionFactory;

    public ReactiveRedisScriptExecutor(ReactiveRedisConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "Reactive Redis 连接工厂不可为空");
    }

    private static ByteBuffer[] encode(List<String> keys, List<String> arguments) {
        List<ByteBuffer> encoded = new ArrayList<>(keys.size() + arguments.size());
        keys.forEach(value -> encoded.add(StandardCharsets.UTF_8.encode(value)));
        arguments.forEach(value -> encoded.add(StandardCharsets.UTF_8.encode(value)));
        return encoded.toArray(ByteBuffer[]::new);
    }

    private static boolean isRedisFailure(Throwable error) {
        if (error instanceof GuardDependencyUnavailableException) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof RedisConnectionFailureException || current instanceof RedisSystemException
                || current instanceof QueryTimeoutException || current instanceof RedisException
                || current instanceof TimeoutException || current instanceof IOException) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    private static List<Long> validateSingleReply(List<?> replies, int expectedReplyArity) {
        if (replies.size() != 1) {
            throw new GuardDependencyUnavailableException("Redis Guard 脚本响应数量无效: count=" + replies.size());
        }
        return validateReply(replies.getFirst(), expectedReplyArity);
    }

    private static List<Long> validateReply(Object aggregateReply, int expectedReplyArity) {
        if (!(aggregateReply instanceof List<?> reply)) {
            throw new GuardDependencyUnavailableException("Redis Guard 脚本响应不是聚合列表");
        }
        if (reply.size() != expectedReplyArity || reply.stream().anyMatch(value -> !(value instanceof Long))) {
            throw new GuardDependencyUnavailableException("Redis Guard 脚本响应格式无效: arity=" + reply.size() + ", types="
                + reply.stream().map(value -> value == null ? "null" : value.getClass().getName()).toList());
        }
        return reply.stream().map(Long.class::cast).toList();
    }

    public LoadedScript loadUtf8Script(String classpathLocation) {
        try {
            byte[] bytes = new ClassPathResource(classpathLocation).getContentAsByteArray();
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return new LoadedScript(bytes);
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("Redis Lua 资源不是有效 UTF-8: " + classpathLocation, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Redis Lua 资源无法读取: " + classpathLocation, exception);
        }
    }

    public Mono<List<Long>> execute(LoadedScript script, List<String> keys, List<String> arguments,
        int expectedReplyArity) {
        Objects.requireNonNull(script, "Redis Lua 脚本不可为空");
        List<String> immutableKeys = List.copyOf(Objects.requireNonNull(keys, "Redis Lua Keys 不可为空"));
        List<String> immutableArguments = List.copyOf(Objects.requireNonNull(arguments, "Redis Lua 参数不可为空"));
        if (immutableKeys.isEmpty() || expectedReplyArity <= 0) {
            throw new IllegalArgumentException("Redis Lua 必须包含 Key 和正数响应长度");
        }
        return Mono.defer(() -> Mono.usingWhen(Mono.fromSupplier(connectionFactory::getReactiveConnection),
            connection -> evaluate(connection, script, immutableKeys, immutableArguments, expectedReplyArity),
            ReactiveRedisConnection::closeLater, (connection, error) -> connection.closeLater(),
            ReactiveRedisConnection::closeLater)).onErrorMap(ReactiveRedisScriptExecutor::isRedisFailure,
                error -> new GuardDependencyUnavailableException("Redis Guard 脚本执行失败", error));
    }

    private Mono<List<Long>> evaluate(ReactiveRedisConnection connection, LoadedScript script, List<String> keys,
        List<String> arguments, int expectedReplyArity) {
        ByteBuffer[] keysAndArguments = encode(keys, arguments);
        return connection.scriptingCommands().eval(script.buffer(), ReturnType.MULTI, keys.size(), keysAndArguments)
            .collectList().map(replies -> validateSingleReply(replies, expectedReplyArity));
    }

    /** 启动期已完成 UTF-8 校验的不可变 Lua 字节。 */
    public static final class LoadedScript {
        private final byte[] bytes;

        private LoadedScript(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        private ByteBuffer buffer() {
            return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
        }
    }
}
