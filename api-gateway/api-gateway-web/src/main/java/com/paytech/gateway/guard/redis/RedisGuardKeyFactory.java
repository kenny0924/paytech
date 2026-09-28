package com.paytech.gateway.guard.redis;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.concurrency.ConcurrencyKeys;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.RawGuardIdentity;
import com.paytech.gateway.routing.OperationsRouteCode;

/** 创建语法受限、版本化且保留规范化原始身份的 Redis Guard Key。 */
@Component
public final class RedisGuardKeyFactory {
    private static final Pattern ROUTE_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final String guardPrefix;

    public RedisGuardKeyFactory(GuardRedisProperties properties) {
        GuardRedisProperties validated = Objects.requireNonNull(properties, "Redis Guard 配置不可为空");
        guardPrefix = validated.namespace() + ":guard:v3:";
    }

    public String rateKey(String routeCode, GuardDimension dimension) {
        return sharedKey(routeCode, "rate", dimension);
    }

    public String rateKey(String routeCode, GuardDimension dimension, String rawIdentity) {
        return identityKey(routeCode, "rate", dimension, rawIdentity);
    }

    public String concurrencyKey(String routeCode, GuardDimension dimension) {
        return sharedKey(routeCode, "concurrency", dimension);
    }

    public String concurrencyKey(String routeCode, GuardDimension dimension, String rawIdentity) {
        return identityKey(routeCode, "concurrency", dimension, rawIdentity);
    }

    public ConcurrencyKeys concurrencyKeys(String routeCode, GuardDimension dimension) {
        return ConcurrencyKeys.fromBaseKey(concurrencyKey(routeCode, dimension));
    }

    public ConcurrencyKeys concurrencyKeys(String routeCode, GuardDimension dimension, String rawIdentity) {
        return ConcurrencyKeys.fromBaseKey(concurrencyKey(routeCode, dimension, rawIdentity));
    }

    public String rateKey(OperationsRouteCode routeCode, GuardDimension dimension) {
        return rateKey(routeCode.getCode(), dimension);
    }

    public String rateKey(OperationsRouteCode routeCode, GuardDimension dimension, String rawIdentity) {
        return rateKey(routeCode.getCode(), dimension, rawIdentity);
    }

    public String concurrencyKey(OperationsRouteCode routeCode, GuardDimension dimension) {
        return concurrencyKey(routeCode.getCode(), dimension);
    }

    public String concurrencyKey(OperationsRouteCode routeCode, GuardDimension dimension, String rawIdentity) {
        return concurrencyKey(routeCode.getCode(), dimension, rawIdentity);
    }

    public ConcurrencyKeys concurrencyKeys(OperationsRouteCode routeCode, GuardDimension dimension) {
        return concurrencyKeys(routeCode.getCode(), dimension);
    }

    public ConcurrencyKeys concurrencyKeys(OperationsRouteCode routeCode, GuardDimension dimension,
        String rawIdentity) {
        return concurrencyKeys(routeCode.getCode(), dimension, rawIdentity);
    }

    private String sharedKey(String routeCode, String policy, GuardDimension dimension) {
        GuardDimension validatedDimension = Objects.requireNonNull(dimension, "Redis Guard 维度不可为空");
        if (validatedDimension != GuardDimension.SHARED_ROUTE) {
            throw new IllegalArgumentException("身份维度 Redis Guard Key 必须包含原始身份");
        }
        return prefix(routeCode, policy) + validatedDimension.getCode() + ":shared";
    }

    private String identityKey(String routeCode, String policy, GuardDimension dimension, String rawIdentity) {
        GuardDimension validatedDimension = Objects.requireNonNull(dimension, "Redis Guard 维度不可为空");
        if (validatedDimension == GuardDimension.SHARED_ROUTE) {
            throw new IllegalArgumentException("SHARED_ROUTE Redis Guard Key 不可包含原始身份");
        }
        return prefix(routeCode, policy) + validatedDimension.getCode() + ":"
            + RawGuardIdentity.requireValid(rawIdentity);
    }

    private String prefix(String routeCode, String policy) {
        String validatedRoute = Objects.requireNonNull(routeCode, "Redis Guard 路由编码不可为空");
        if (!ROUTE_PATTERN.matcher(validatedRoute).matches()) {
            throw new IllegalArgumentException("Redis Guard 路由编码格式无效");
        }
        return guardPrefix + validatedRoute + ":" + policy + ":";
    }

}
