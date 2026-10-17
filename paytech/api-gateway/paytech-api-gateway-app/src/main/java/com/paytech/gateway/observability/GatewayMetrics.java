package com.paytech.gateway.observability;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Gateway 自定义可观测信号的唯一入口。 */
@Component
public final class GatewayMetrics {
    public static final String REQUEST_TIMER = "gateway.requests";
    public static final String UPSTREAM_LATENCY_TIMER = "gateway.upstream.latency";
    public static final String GUARD_REJECTION_COUNTER = "gateway.guard.rejections";
    public static final String JWKS_REFRESH_COUNTER = "gateway.jwks.refreshes";
    public static final String SNAPSHOT_REFRESH_COUNTER = "gateway.snapshot.refreshes";
    public static final String CONCURRENCY_RELEASE_FAILURE_COUNTER = "gateway.guard.concurrency.release.failures";
    private static final String UNMATCHED_ROUTE = "UNMATCHED";
    private static final String UNMATCHED_CATALOG_VALUE = "UNMATCHED";
    private static final Set<String> CHANNELS =
        Set.of("OPERATIONS", "CLIENT", "PARTNER", "CHECKOUT", "CLIENT_API", "CHANNEL_CALLBACK");
    private static final Set<String> PROFILES = Set.of("PUBLIC", "OPERATIONS_JWT", "CLIENT_JWT", "PARTNER_JWT",
        "CLIENT_API_CREDENTIAL", "CHECKOUT_SESSION", "CHANNEL_CALLBACK");
    private static final Set<String> STATUS_FAMILIES = Set.of("1xx", "2xx", "3xx", "4xx", "5xx", "NONE");

    private final MeterRegistry meterRegistry;
    private final Counter ingressConcurrencyReleaseFailures;
    private final Counter authenticatedConcurrencyReleaseFailures;
    private final Counter successfulJwksRefreshes;
    private final Counter failedJwksRefreshes;
    private final Counter suppressedJwksRefreshes;
    private final Counter successfulSnapshotRefreshes;
    private final Counter failedSnapshotRefreshes;
    private final Counter suppressedSnapshotRefreshes;

    public GatewayMetrics(MeterRegistry meterRegistry) {
        MeterRegistry registry = Objects.requireNonNull(meterRegistry, "Gateway MeterRegistry 不可为空");
        this.meterRegistry = registry;
        ingressConcurrencyReleaseFailures = releaseFailureCounter(registry, GuardPhase.INGRESS);
        authenticatedConcurrencyReleaseFailures = releaseFailureCounter(registry, GuardPhase.AUTHENTICATED_SUBJECT);
        successfulJwksRefreshes = jwksRefreshCounter(registry, "success");
        failedJwksRefreshes = jwksRefreshCounter(registry, "failure");
        suppressedJwksRefreshes = jwksRefreshCounter(registry, "suppressed");
        successfulSnapshotRefreshes = snapshotRefreshCounter(registry, "success");
        failedSnapshotRefreshes = snapshotRefreshCounter(registry, "failure");
        suppressedSnapshotRefreshes = snapshotRefreshCounter(registry, "suppressed");
    }

    public void recordRequest(String routeCode, GatewayRouteChannel channel, String profile, GatewayOutcome outcome,
        String statusFamily, long durationNanos) {
        Timer.builder(REQUEST_TIMER).description("Gateway terminal request duration")
            .tags("route", normalizedRoute(routeCode), "channel", normalizedChannel(channel), "profile",
                normalizedProfile(profile), "outcome", Objects.requireNonNull(outcome, "Gateway Outcome 不可为空").name(),
                "status_family", normalizedStatusFamily(statusFamily))
            .register(meterRegistry).record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);
    }

    public void recordUpstreamLatency(String routeCode, GatewayRouteChannel channel, String profile, long durationNanos) {
        Timer.builder(UPSTREAM_LATENCY_TIMER).description("Gateway upstream exchange duration")
            .tags("route", normalizedRoute(routeCode), "channel", normalizedChannel(channel), "profile",
                normalizedProfile(profile))
            .register(meterRegistry).record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);
    }

    public void recordRateLimitRejection(GatewayRuntimeRoute route, GuardDimension dimension) {
        recordGuardRejection(route, "rate", dimension);
    }

    public void recordConcurrencyLimitRejection(GatewayRuntimeRoute route, GuardDimension dimension) {
        recordGuardRejection(route, "concurrency", dimension);
    }

    public void recordJwksRefreshSuccess() {
        successfulJwksRefreshes.increment();
    }

    public void recordJwksRefreshFailure() {
        failedJwksRefreshes.increment();
    }

    public void recordJwksRefreshSuppressed() {
        suppressedJwksRefreshes.increment();
    }

    public void recordSnapshotRefreshSuccess() {
        successfulSnapshotRefreshes.increment();
    }

    public void recordSnapshotRefreshFailure() {
        failedSnapshotRefreshes.increment();
    }

    public void recordSnapshotRefreshSuppressed() {
        suppressedSnapshotRefreshes.increment();
    }

    public void recordConcurrencyReleaseFailure(GuardPhase phase) {
        Objects.requireNonNull(phase, "Guard Phase 不可为空");
        if (phase == GuardPhase.INGRESS) {
            ingressConcurrencyReleaseFailures.increment();
            return;
        }
        authenticatedConcurrencyReleaseFailures.increment();
    }

    private void recordGuardRejection(GatewayRuntimeRoute route, String limiter, GuardDimension dimension) {
        GatewayRuntimeRoute runtimeRoute = Objects.requireNonNull(route, "Gateway Runtime Route 不可为空");
        GuardDimension rejectingDimension = Objects.requireNonNull(dimension, "拒绝 Guard 维度不可为空");
        Counter.builder(GUARD_REJECTION_COUNTER)
            .description("Gateway Guard rejected atomic admissions by actual rejecting dimension")
            .tags("route", normalizedRoute(runtimeRoute.routeCode()), "channel",
                normalizedChannel(runtimeRoute.channel()), "profile",
                normalizedProfile(runtimeRoute.securityProfileCode()), "limiter", limiter, "dimension",
                rejectingDimension.getCode())
            .register(meterRegistry).increment();
    }

    private Counter jwksRefreshCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(JWKS_REFRESH_COUNTER).tag("outcome", outcome)
            .description("Operations JWKS refresh attempts").register(registry);
    }

    private Counter snapshotRefreshCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(SNAPSHOT_REFRESH_COUNTER).tag("outcome", outcome)
            .description("Platform public Gateway Snapshot refresh attempts").register(registry);
    }

    private Counter releaseFailureCounter(MeterRegistry registry, GuardPhase phase) {
        return Counter.builder(CONCURRENCY_RELEASE_FAILURE_COUNTER).tag("phase", phase.tagValue())
            .description("Gateway Guard concurrency lease release failures").register(registry);
    }

    private String normalizedRoute(String routeCode) {
        return routeCode != null && routeCode.matches("[A-Za-z][A-Za-z0-9._-]{0,127}") ? routeCode : UNMATCHED_ROUTE;
    }

    private String normalizedChannel(GatewayRouteChannel channel) {
        return channel != null && CHANNELS.contains(channel.getCode()) ? channel.getCode() : UNMATCHED_CATALOG_VALUE;
    }

    private String normalizedProfile(String profile) {
        return profile != null && PROFILES.contains(profile) ? profile : UNMATCHED_CATALOG_VALUE;
    }

    private String normalizedStatusFamily(String statusFamily) {
        return STATUS_FAMILIES.contains(statusFamily) ? statusFamily : "NONE";
    }

    /** Guard 两个固定准入阶段的低基数 Metric 标签。 */
    public enum GuardPhase {
        INGRESS("ingress"),

        AUTHENTICATED_SUBJECT("authenticated-subject");

        private final String tagValue;

        GuardPhase(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }
}
