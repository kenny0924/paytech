package com.paytech.gateway.guard.ratelimit;

import java.util.*;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.guard.redis.RedisGuardKeyFactory;

/** 将一个静态 Guard Profile 确定性展开为每维度一个 Rate Bucket 命令。 */
@Component
public final class RateLimitCommandFactory {
    private static final long MICROTOKENS_PER_TOKEN = 1_000_000L;

    private final RedisGuardKeyFactory keyFactory;

    public RateLimitCommandFactory(RedisGuardKeyFactory keyFactory) {
        this.keyFactory = Objects.requireNonNull(keyFactory, "Redis Guard Key 工厂不可为空");
    }

    private static long toMillis(RateLimitRule rule) {
        try {
            return rule.refillPeriod().toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("限流周期超出毫秒范围", exception);
        }
    }

    private static long toMicrotokens(long tokens, String message) {
        if (tokens > RateLimitCommand.MAX_SAFE_INTEGER / MICROTOKENS_PER_TOKEN) {
            throw new IllegalArgumentException(message);
        }
        return tokens * MICROTOKENS_PER_TOKEN;
    }

    public List<RateLimitCommand> create(GuardProfile profile, GuardContext context) {
        return create(profile, context,
            Set.of(GuardDimension.SHARED_ROUTE, GuardDimension.CLIENT_IP, GuardDimension.AUTHENTICATED_SUBJECT,
                GuardDimension.TENANT));
    }

    public List<RateLimitCommand> createIngress(GuardProfile profile, GuardContext context) {
        return create(profile, context, Set.of(GuardDimension.SHARED_ROUTE, GuardDimension.CLIENT_IP));
    }

    public List<RateLimitCommand> createAuthenticatedSubject(GuardProfile profile, GuardContext context) {
        return create(profile, context, Set.of(GuardDimension.AUTHENTICATED_SUBJECT));
    }

    public List<RateLimitCommand> createAuthenticated(GuardProfile profile, GuardContext context) {
        return create(profile, context, Set.of(GuardDimension.AUTHENTICATED_SUBJECT, GuardDimension.TENANT));
    }

    private List<RateLimitCommand> create(GuardProfile profile, GuardContext context,
        Set<GuardDimension> allowedDimensions) {
        GuardProfile validatedProfile = Objects.requireNonNull(profile, "Guard Profile 不可为空");
        GuardContext validatedContext = Objects.requireNonNull(context, "Guard 上下文不可为空");
        List<RateLimitRule> rules = validatedProfile.rateLimits().stream()
            .sorted(Comparator.comparingInt(rule -> rule.dimension().sortOrder())).toList();
        if (rules.stream().anyMatch(rule -> !allowedDimensions.contains(rule.dimension()))) {
            throw new IllegalArgumentException("Rate Guard Profile 包含当前阶段不允许的维度");
        }
        List<RateLimitCommand> commands = new ArrayList<>(rules.size());
        for (RateLimitRule rule : rules) {
            commands.add(toCommand(rule, validatedContext));
        }
        return List.copyOf(commands);
    }

    private RateLimitCommand toCommand(RateLimitRule rule, GuardContext context) {
        String key =
            rule.dimension() == GuardDimension.SHARED_ROUTE ? keyFactory.rateKey(context.routeCode(), rule.dimension())
                : keyFactory.rateKey(context.routeCode(), rule.dimension(), rawIdentity(rule.dimension(), context));
        long capacity = toMicrotokens(rule.capacity(), "限流容量超出精确 Microtoken 范围");
        long refill = toMicrotokens(rule.refillTokens(), "限流补充量超出精确 Microtoken 范围");
        long periodMillis = toMillis(rule);
        return new RateLimitCommand(key, capacity, refill, periodMillis, MICROTOKENS_PER_TOKEN);
    }

    private String rawIdentity(GuardDimension dimension, GuardContext context) {
        if (dimension == GuardDimension.CLIENT_IP) {
            return context.remoteAddress().getHostAddress();
        }
        if (dimension == GuardDimension.AUTHENTICATED_SUBJECT && context.hasAuthenticatedSubject()) {
            return context.authenticatedPrincipal();
        }
        if (dimension == GuardDimension.TENANT && context.hasTenantIdentity()) {
            return context.tenantIdentity();
        }
        throw new IllegalArgumentException("认证后限流维度缺少可信身份");
    }
}
