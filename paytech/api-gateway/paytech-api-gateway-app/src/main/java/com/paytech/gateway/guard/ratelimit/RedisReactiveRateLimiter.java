package com.paytech.gateway.guard.ratelimit;

import java.util.*;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;

import reactor.core.publisher.Mono;

/** 使用单段 Redis Lua 原子评估全部适用 Token Bucket。 */
@Component
public final class RedisReactiveRateLimiter implements ReactiveRateLimiter {
    private static final String SCRIPT_LOCATION = "com/paytech/gateway/guard/ratelimit/token-bucket.lua";

    private final String configuredNamespace;
    private final ReactiveRedisScriptExecutor scriptExecutor;
    private final ReactiveRedisScriptExecutor.LoadedScript script;

    public RedisReactiveRateLimiter(ReactiveRedisScriptExecutor scriptExecutor, GuardRedisProperties properties) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "Redis 脚本执行器不可为空");
        configuredNamespace = Objects.requireNonNull(properties, "Redis Guard 配置不可为空").namespace();
        script = scriptExecutor.loadUtf8Script(SCRIPT_LOCATION);
    }

    private static RateLimitDecision toDecision(List<Long> reply) {
        long allowed = reply.get(0);
        long retryAfterSeconds = reply.get(1);
        long rejectingDimensionCode = reply.get(2);
        if (allowed == 1 && retryAfterSeconds == 0 && rejectingDimensionCode == 0) {
            return RateLimitDecision.allowed(0);
        }
        if (allowed == 0 && retryAfterSeconds >= 1 && retryAfterSeconds <= RateLimitCommand.MAX_SAFE_INTEGER
            && rejectingDimension(rejectingDimensionCode) != null) {
            return RateLimitDecision.rejected(retryAfterSeconds, rejectingDimension(rejectingDimensionCode));
        }
        throw new GuardDependencyUnavailableException("Redis Token Bucket 响应语义无效");
    }

    private static GuardDimension rejectingDimension(long code) {
        return Arrays.stream(GuardDimension.values()).filter(dimension -> dimension.sortOrder() == code).findFirst()
            .orElse(null);
    }

    @Override
    public Mono<RateLimitDecision> evaluate(List<RateLimitCommand> commands) {
        List<RateLimitCommand> immutableCommands = List.copyOf(Objects.requireNonNull(commands, "限流命令不可为空"));
        if (immutableCommands.isEmpty()) {
            throw new IllegalArgumentException("限流命令不可为空");
        }
        List<String> keys = new ArrayList<>(immutableCommands.size());
        List<String> arguments = new ArrayList<>(1 + immutableCommands.size() * 4);
        arguments.add(configuredNamespace);
        Set<String> uniqueKeys = new HashSet<>();
        for (RateLimitCommand command : immutableCommands) {
            if (!uniqueKeys.add(command.key())) {
                throw new IllegalArgumentException("限流命令 Redis Key 不可重复");
            }
            keys.add(command.key());
            arguments.add(Long.toString(command.capacityMicrotokens()));
            arguments.add(Long.toString(command.refillMicrotokens()));
            arguments.add(Long.toString(command.refillPeriodMillis()));
            arguments.add(Long.toString(command.requestedMicrotokens()));
        }
        return scriptExecutor.execute(script, keys, arguments, 3).map(RedisReactiveRateLimiter::toDecision);
    }
}
