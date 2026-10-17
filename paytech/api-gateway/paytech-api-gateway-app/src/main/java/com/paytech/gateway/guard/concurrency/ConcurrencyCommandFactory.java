package com.paytech.gateway.guard.concurrency;

import java.time.Duration;
import java.util.*;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.redis.RedisGuardKeyFactory;

/** 将一个静态 Guard Profile 确定性展开为每维度一个 Concurrency Lease 命令。 */
@Component
public final class ConcurrencyCommandFactory {
    private final RedisGuardKeyFactory keyFactory;

    public ConcurrencyCommandFactory(RedisGuardKeyFactory keyFactory) {
        this.keyFactory = Objects.requireNonNull(keyFactory, "Redis Guard Key 工厂不可为空");
    }

    private static long toMillis(Duration duration, String message) {
        try {
            return duration.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    public List<ConcurrencyCommand> create(GuardProfile profile, GuardContext context) {
        return create(profile, context,
            Set.of(GuardDimension.SHARED_ROUTE, GuardDimension.CLIENT_IP, GuardDimension.AUTHENTICATED_SUBJECT,
                GuardDimension.TENANT));
    }

    public List<ConcurrencyCommand> createIngress(GuardProfile profile, GuardContext context) {
        return create(profile, context, Set.of(GuardDimension.SHARED_ROUTE, GuardDimension.CLIENT_IP));
    }

    public List<ConcurrencyCommand> createAuthenticatedSubject(GuardProfile profile, GuardContext context) {
        return create(profile, context, Set.of(GuardDimension.AUTHENTICATED_SUBJECT));
    }

    public List<ConcurrencyCommand> createAuthenticated(GuardProfile profile, GuardContext context) {
        return create(profile, context, Set.of(GuardDimension.AUTHENTICATED_SUBJECT, GuardDimension.TENANT));
    }

    private List<ConcurrencyCommand> create(GuardProfile profile, GuardContext context,
        Set<GuardDimension> allowedDimensions) {
        GuardProfile validatedProfile = Objects.requireNonNull(profile, "Guard Profile 不可为空");
        GuardContext validatedContext = Objects.requireNonNull(context, "Guard 上下文不可为空");
        long leaseDurationMillis = toMillis(validatedProfile.leaseDuration(), "并发 Lease 时长超出毫秒范围");
        long stateTtlMillis = toMillis(validatedProfile.stateTtl(), "并发状态 TTL 超出毫秒范围");
        List<ConcurrencyRule> rules = validatedProfile.concurrencyLimits().stream()
            .sorted(Comparator.comparingInt(rule -> rule.dimension().sortOrder())).toList();
        if (rules.stream().anyMatch(rule -> !allowedDimensions.contains(rule.dimension()))) {
            throw new IllegalArgumentException("Concurrency Guard Profile 包含当前阶段不允许的维度");
        }
        List<ConcurrencyCommand> commands = new ArrayList<>(rules.size());
        for (ConcurrencyRule rule : rules) {
            commands.add(toCommand(rule, validatedContext, leaseDurationMillis, stateTtlMillis));
        }
        return List.copyOf(commands);
    }

    private ConcurrencyCommand toCommand(ConcurrencyRule rule, GuardContext context, long leaseDurationMillis,
        long stateTtlMillis) {
        ConcurrencyKeys keys = rule.dimension() == GuardDimension.SHARED_ROUTE
            ? keyFactory.concurrencyKeys(context.routeCode(), rule.dimension())
            : keyFactory.concurrencyKeys(context.routeCode(), rule.dimension(), rawIdentity(rule.dimension(), context));
        return new ConcurrencyCommand(keys, rule.limit(), leaseDurationMillis, stateTtlMillis);
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
        throw new IllegalArgumentException("认证后并发维度缺少可信身份");
    }
}
