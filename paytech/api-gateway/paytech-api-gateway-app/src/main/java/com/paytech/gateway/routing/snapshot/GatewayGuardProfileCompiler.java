package com.paytech.gateway.routing.snapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;

/** 将平台 Guard DTO 编译为 Gateway 可直接执行的两阶段 Guard Profile。 */
final class GatewayGuardProfileCompiler {
    private static final long MAX_RATE_VALUE = 600;
    private static final long MAX_CONCURRENCY = 50;
    private static final int MAX_GUARD_RULES = 8;

    GuardProfiles compile(List<GatewayGuardRuleSnapshot> rules, String securityProfile) {
        if (rules == null || rules.size() > MAX_GUARD_RULES) {
            throw invalid("Gateway Guard 不可为空");
        }
        MutableProfiles profiles = new MutableProfiles();
        Set<String> identities = new HashSet<>();
        GatewaySecurityProfileCode profile = GatewaySnapshotCodeParser.securityProfile(securityProfile);
        for (GatewayGuardRuleSnapshot rule : rules) {
            compileRule(rule, profile, identities, profiles);
        }
        validateBaseline(profiles, profile);
        GuardProfile authenticated = profiles.authenticatedRate.isEmpty() && profiles.authenticatedConcurrency.isEmpty()
            ? null : profile(profiles.authenticatedRate, profiles.authenticatedConcurrency);
        return new GuardProfiles(profile(profiles.ingressRate, profiles.ingressConcurrency), authenticated);
    }

    private void compileRule(GatewayGuardRuleSnapshot rule, GatewaySecurityProfileCode profile, Set<String> identities,
        MutableProfiles profiles) {
        if (rule == null) {
            throw invalid("Gateway Guard 重复或为空");
        }
        GatewayGuardRuleType ruleType = GatewaySnapshotCodeParser.guardRuleType(rule.getRuleType());
        GatewayGuardDimension platformDimension = GatewaySnapshotCodeParser.guardDimension(rule.getDimension());
        GuardDimension dimension = dimension(platformDimension);
        if (!identities.add(ruleType.getCode() + ':' + platformDimension.getCode())) {
            throw invalid("Gateway Guard 重复或为空");
        }
        boolean authenticated = platformDimension == GatewayGuardDimension.AUTHENTICATED_PRINCIPAL
            || platformDimension == GatewayGuardDimension.TENANT;
        if (authenticated && profile == GatewaySecurityProfileCode.PUBLIC) {
            throw invalid("PUBLIC Route 不可使用身份 Guard");
        }
        if (ruleType == GatewayGuardRuleType.RATE_LIMIT && validRate(rule)) {
            RateLimitRule value = new RateLimitRule(dimension, rule.getCapacity(), rule.getRefillTokens(),
                Duration.ofMillis(rule.getRefillPeriodMillis()));
            (authenticated ? profiles.authenticatedRate : profiles.ingressRate).add(value);
            return;
        }
        if (ruleType == GatewayGuardRuleType.CONCURRENCY && validConcurrency(rule)) {
            ConcurrencyRule value = new ConcurrencyRule(dimension, Math.toIntExact(rule.getConcurrencyLimit()));
            (authenticated ? profiles.authenticatedConcurrency : profiles.ingressConcurrency).add(value);
            return;
        }
        throw invalid("Gateway Guard 值非法");
    }

    private void validateBaseline(MutableProfiles profiles, GatewaySecurityProfileCode securityProfile) {
        if (profiles.ingressRate.stream().noneMatch(rule -> rule.dimension() == GuardDimension.SHARED_ROUTE)
            || profiles.ingressConcurrency.stream()
                .noneMatch(rule -> rule.dimension() == GuardDimension.SHARED_ROUTE)) {
            throw invalid("Gateway Guard 缺少 Shared Route 基线");
        }
        requirePair(profiles.ingressRate, profiles.ingressConcurrency, GuardDimension.CLIENT_IP,
            GatewayGuardDimension.CLIENT_IP.getCode());
        requirePair(profiles.authenticatedRate, profiles.authenticatedConcurrency, GuardDimension.AUTHENTICATED_SUBJECT,
            GatewayGuardDimension.AUTHENTICATED_PRINCIPAL.getCode());
        requirePair(profiles.authenticatedRate, profiles.authenticatedConcurrency, GuardDimension.TENANT,
            GatewayGuardDimension.TENANT.getCode());
        if ((securityProfile == GatewaySecurityProfileCode.OPERATIONS_JWT
                || securityProfile == GatewaySecurityProfileCode.CLIENT_JWT)
            && (profiles.ingressRate.stream().noneMatch(rule -> rule.dimension() == GuardDimension.CLIENT_IP)
                || profiles.authenticatedRate.stream()
                    .noneMatch(rule -> rule.dimension() == GuardDimension.AUTHENTICATED_SUBJECT))) {
            throw invalid(securityProfile.getCode() + " Route 缺少 IP 或认证主体 Guard 基线");
        }
        if (securityProfile == GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL
            && (profiles.ingressRate.stream().noneMatch(rule -> rule.dimension() == GuardDimension.CLIENT_IP)
                || profiles.authenticatedRate.stream()
                    .noneMatch(rule -> rule.dimension() == GuardDimension.AUTHENTICATED_SUBJECT)
                || profiles.authenticatedRate.stream().noneMatch(rule -> rule.dimension() == GuardDimension.TENANT))) {
            throw invalid("CLIENT_API_CREDENTIAL Route 缺少 IP、认证主体或 Tenant Guard 基线");
        }
    }

    private boolean validRate(GatewayGuardRuleSnapshot rule) {
        return rule.getCapacity() != null && rule.getRefillTokens() != null && rule.getRefillPeriodMillis() != null
            && rule.getConcurrencyLimit() == null && rule.getCapacity() > 0 && rule.getRefillTokens() > 0
            && rule.getRefillPeriodMillis() > 0 && rule.getCapacity() <= MAX_RATE_VALUE
            && rule.getRefillTokens() <= MAX_RATE_VALUE && rule.getRefillPeriodMillis() <= 60_000L;
    }

    private boolean validConcurrency(GatewayGuardRuleSnapshot rule) {
        return rule.getCapacity() == null && rule.getRefillTokens() == null && rule.getRefillPeriodMillis() == null
            && rule.getConcurrencyLimit() != null && rule.getConcurrencyLimit() > 0
            && rule.getConcurrencyLimit() <= MAX_CONCURRENCY;
    }

    private GuardProfile profile(List<RateLimitRule> rates, List<ConcurrencyRule> concurrency) {
        return new GuardProfile(rates, concurrency, Duration.ofSeconds(30), Duration.ofMinutes(1));
    }

    private void requirePair(List<RateLimitRule> rates, List<ConcurrencyRule> concurrency, GuardDimension dimension,
        String name) {
        boolean rate = rates.stream().anyMatch(rule -> rule.dimension() == dimension);
        boolean limit = concurrency.stream().anyMatch(rule -> rule.dimension() == dimension);
        if (rate != limit) {
            throw invalid("Gateway Guard 维度必须同时声明 Rate 与 Concurrency: " + name);
        }
    }

    private GuardDimension dimension(GatewayGuardDimension value) {
        return switch (value) {
            case SHARED_ROUTE -> GuardDimension.SHARED_ROUTE;
            case CLIENT_IP -> GuardDimension.CLIENT_IP;
            case AUTHENTICATED_PRINCIPAL -> GuardDimension.AUTHENTICATED_SUBJECT;
            case TENANT -> GuardDimension.TENANT;
        };
    }

    private InvalidGatewaySnapshotException invalid(String message) {
        return new InvalidGatewaySnapshotException(message);
    }

    record GuardProfiles(GuardProfile ingress, GuardProfile authenticated) {
    }

    private static final class MutableProfiles {
        private final List<RateLimitRule> ingressRate = new ArrayList<>();
        private final List<ConcurrencyRule> ingressConcurrency = new ArrayList<>();
        private final List<RateLimitRule> authenticatedRate = new ArrayList<>();
        private final List<ConcurrencyRule> authenticatedConcurrency = new ArrayList<>();
    }
}
