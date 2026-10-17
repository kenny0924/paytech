package com.paytech.gateway.idempotency;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;

import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 一个 Key 一个 Redis Hash，claim / complete / release 均由 Lua 原子执行。 */
@Component
public class RedisIdempotencyRecordStore implements IdempotencyRecordStore {
    private static final String SCRIPT_DIRECTORY = "com/paytech/gateway/idempotency/";
    private static final TypeReference<LinkedHashMap<String, List<String>>> HEADERS_TYPE = new TypeReference<>() {};
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ReactiveRedisScriptExecutor scriptExecutor;
    private final ReactiveRedisScriptExecutor.LoadedScript claimScript;
    private final ReactiveRedisScriptExecutor.LoadedScript completeScript;
    private final ReactiveRedisScriptExecutor.LoadedScript releaseScript;
    private final String keyPrefix;

    public RedisIdempotencyRecordStore(ReactiveRedisScriptExecutor scriptExecutor, IdempotencyProperties properties,
        GuardRedisProperties guardRedisProperties) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "Redis 脚本执行器不可为空");
        String configured = properties.redis().namespace();
        // 复用 Guard Namespace 的语法校验，避免出现 Glob 或嵌入版本段的前缀。
        String namespace = configured == null || configured.isBlank() ? guardRedisProperties.namespace()
            : new GuardRedisProperties(configured).namespace();
        keyPrefix = namespace + ":idem:v1:";
        claimScript = scriptExecutor.loadUtf8Script(SCRIPT_DIRECTORY + "idempotency-claim.lua");
        completeScript = scriptExecutor.loadUtf8Script(SCRIPT_DIRECTORY + "idempotency-complete.lua");
        releaseScript = scriptExecutor.loadUtf8Script(SCRIPT_DIRECTORY + "idempotency-release.lua");
    }

    @Override
    public Mono<Claim> claim(Scope scope, String requestDigest, Duration pendingLease) {
        String recordKey = keyPrefix + scope.channel() + ":" + scope.callerKey() + ":" + scope.keyHash();
        String owner = UUID.randomUUID().toString();
        List<byte[]> arguments = List.of(utf8(requestDigest), utf8(owner), utf8(Long.toString(pendingLease.toMillis())));
        return scriptExecutor.executeForBulkStrings(claimScript, List.of(recordKey), arguments)
            .map(reply -> toClaim(reply, new Lease(recordKey, owner)));
    }

    @Override
    public Mono<Boolean> complete(Lease lease, StoredResponse response, Duration retention) {
        List<byte[]> arguments = List.of(utf8(lease.owner()), utf8(Integer.toString(response.status())),
            JSON.writeValueAsBytes(response.headers()), gzip(response.body()),
            utf8(Long.toString(retention.toMillis())));
        return scriptExecutor.executeForBulkStrings(completeScript, List.of(lease.recordKey()), arguments)
            .map(reply -> "COMPLETED".equals(text(reply.getFirst())));
    }

    @Override
    public Mono<Void> release(Lease lease) {
        return scriptExecutor.executeForBulkStrings(releaseScript, List.of(lease.recordKey()),
            List.of(utf8(lease.owner()))).then();
    }

    private Claim toClaim(List<byte[]> reply, Lease lease) {
        String outcome = text(reply.getFirst());
        return switch (outcome) {
            case "CLAIMED" -> new Claim(ClaimOutcome.CLAIMED, lease, null);
            case "IN_PROGRESS" -> new Claim(ClaimOutcome.IN_PROGRESS, null, null);
            case "KEY_REUSED" -> new Claim(ClaimOutcome.KEY_REUSED, null, null);
            case "COMPLETED" -> new Claim(ClaimOutcome.COMPLETED, null, storedResponse(reply));
            default -> throw new GuardDependencyUnavailableException("Redis 请求幂等占位响应语义无效");
        };
    }

    private StoredResponse storedResponse(List<byte[]> reply) {
        if (reply.size() != 4) {
            throw new GuardDependencyUnavailableException("Redis 请求幂等完成记录格式无效");
        }
        try {
            int status = Integer.parseInt(text(reply.get(1)));
            Map<String, List<String>> headers = JSON.readValue(reply.get(2), HEADERS_TYPE);
            return new StoredResponse(status, headers, gunzip(reply.get(3)));
        } catch (RuntimeException exception) {
            throw new GuardDependencyUnavailableException("Redis 请求幂等完成记录无法解析", exception);
        }
    }

    private static byte[] gzip(byte[] body) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(body);
        } catch (IOException exception) {
            throw new UncheckedIOException("请求幂等响应体压缩失败", exception);
        }
        return output.toByteArray();
    }

    private static byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("请求幂等响应体解压失败", exception);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
