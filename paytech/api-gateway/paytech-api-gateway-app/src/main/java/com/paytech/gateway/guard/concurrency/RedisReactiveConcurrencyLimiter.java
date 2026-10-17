package com.paytech.gateway.guard.concurrency;

import java.util.*;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;

import reactor.core.publisher.Mono;

/** 使用 Redis Lua 原子获取和释放全部适用维度的并发 Lease。 */
@Component
public final class RedisReactiveConcurrencyLimiter implements ReactiveConcurrencyLimiter {
    private static final String ACQUIRE_SCRIPT_LOCATION =
        "com/paytech/gateway/guard/concurrency/concurrency-acquire.lua";
    private static final String RELEASE_SCRIPT_LOCATION =
        "com/paytech/gateway/guard/concurrency/concurrency-release.lua";

    private final ReactiveRedisScriptExecutor scriptExecutor;
    private final ReactiveRedisScriptExecutor.LoadedScript acquireScript;
    private final ReactiveRedisScriptExecutor.LoadedScript releaseScript;
    private final String configuredNamespace;

    public RedisReactiveConcurrencyLimiter(ReactiveRedisScriptExecutor scriptExecutor,
        GuardRedisProperties redisProperties) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "Redis 脚本执行器不可为空");
        configuredNamespace = Objects.requireNonNull(redisProperties, "Redis Guard 配置不可为空").namespace();
        acquireScript = scriptExecutor.loadUtf8Script(ACQUIRE_SCRIPT_LOCATION);
        releaseScript = scriptExecutor.loadUtf8Script(RELEASE_SCRIPT_LOCATION);
    }

    private static List<String> acquireKeys(List<ConcurrencyCommand> commands) {
        List<String> keys = new ArrayList<>(commands.size() * 2);
        for (ConcurrencyCommand command : commands) {
            keys.add(command.keys().leaseKey());
            keys.add(command.keys().metadataKey());
        }
        return List.copyOf(keys);
    }

    private static ConcurrencyDecision toDecision(List<Long> reply, String leaseId, List<String> keys) {
        long allowed = reply.get(0);
        long retryAfterSeconds = reply.get(1);
        long rejectingDimensionCode = reply.get(2);
        if (allowed == 1 && retryAfterSeconds == 0 && rejectingDimensionCode == 0) {
            return ConcurrencyDecision.allowed(new ConcurrencyLease(leaseId, keys));
        }
        if (allowed == 0 && retryAfterSeconds >= 1 && retryAfterSeconds <= ConcurrencyCommand.MAX_SAFE_INTEGER
            && rejectingDimension(rejectingDimensionCode) != null) {
            return ConcurrencyDecision.rejected(retryAfterSeconds, rejectingDimension(rejectingDimensionCode));
        }
        throw new GuardDependencyUnavailableException("Redis Concurrency Acquire 响应语义无效");
    }

    private static GuardDimension rejectingDimension(long code) {
        return Arrays.stream(GuardDimension.values()).filter(dimension -> dimension.sortOrder() == code).findFirst()
            .orElse(null);
    }

    private static Mono<Void> validateReleaseReply(List<Long> reply) {
        long removed = reply.getFirst();
        if (removed < 0 || removed > ConcurrencyCommand.MAX_SAFE_INTEGER) {
            return Mono.error(new GuardDependencyUnavailableException("Redis Concurrency Release 响应语义无效"));
        }
        return Mono.empty();
    }

    @Override
    public Mono<ConcurrencyDecision> acquire(List<ConcurrencyCommand> commands) {
        List<ConcurrencyCommand> immutableCommands = validatedCommands(commands);
        String leaseId = UUID.randomUUID().toString();
        List<String> keys = acquireKeys(immutableCommands);
        List<String> leaseKeys = immutableCommands.stream().map(ConcurrencyCommand::key).toList();
        List<String> arguments = acquireArguments(leaseId, immutableCommands);
        return scriptExecutor.execute(acquireScript, keys, arguments, 3)
            .map(reply -> toDecision(reply, leaseId, leaseKeys));
    }

    @Override
    public Mono<Void> release(ConcurrencyLease lease) {
        ConcurrencyLease validatedLease = Objects.requireNonNull(lease, "并发 Lease 不可为空");
        validatedLease.leaseKeys().forEach(key -> ConcurrencyKeys.requireConfiguredLeaseKey(key, configuredNamespace));
        return scriptExecutor.execute(releaseScript, validatedLease.leaseKeys(),
            List.of(validatedLease.leaseId(), configuredNamespace), 1)
            .flatMap(RedisReactiveConcurrencyLimiter::validateReleaseReply);
    }

    private List<ConcurrencyCommand> validatedCommands(List<ConcurrencyCommand> commands) {
        List<ConcurrencyCommand> immutableCommands = List.copyOf(Objects.requireNonNull(commands, "并发命令不可为空"));
        if (immutableCommands.isEmpty()) {
            throw new IllegalArgumentException("并发命令不可为空");
        }
        Set<String> uniqueKeys = new HashSet<>();
        for (ConcurrencyCommand command : immutableCommands) {
            ConcurrencyKeys.requireConfiguredNamespace(command.keys(), configuredNamespace);
            if (!uniqueKeys.add(command.keys().leaseKey()) || !uniqueKeys.add(command.keys().metadataKey())) {
                throw new IllegalArgumentException("并发命令 Redis Key 不可重复");
            }
        }
        return immutableCommands;
    }

    private List<String> acquireArguments(String leaseId, List<ConcurrencyCommand> commands) {
        List<String> arguments = new ArrayList<>(2 + commands.size() * 3);
        arguments.add(leaseId);
        arguments.add(configuredNamespace);
        for (ConcurrencyCommand command : commands) {
            arguments.add(Integer.toString(command.limit()));
            arguments.add(Long.toString(command.leaseDurationMillis()));
            arguments.add(Long.toString(command.stateTtlMillis()));
        }
        return List.copyOf(arguments);
    }
}
