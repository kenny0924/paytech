package com.paytech.gateway.guard;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.guard.concurrency.ConcurrencyCommand;
import com.paytech.gateway.guard.concurrency.ConcurrencyDecision;
import com.paytech.gateway.guard.concurrency.ConcurrencyLease;
import com.paytech.gateway.guard.concurrency.ReactiveConcurrencyLimiter;
import com.paytech.gateway.guard.ratelimit.RateLimitCommand;
import com.paytech.gateway.guard.ratelimit.RateLimitDecision;
import com.paytech.gateway.guard.ratelimit.ReactiveRateLimiter;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.observability.GatewayMetrics.GuardPhase;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** 执行一个 Guard 阶段的 Rate、Concurrency 与 cancel-safe Lease 生命周期。 */
@Component
public final class GatewayGuardAdmission {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayGuardAdmission.class);

    private final ReactiveRateLimiter rateLimiter;
    private final ReactiveConcurrencyLimiter concurrencyLimiter;
    private final GatewayErrorWriter errorWriter;
    private final GatewayMetrics gatewayMetrics;
    private final Supplier<Mono<Void>> ownershipHandoffGate;

    @Autowired
    public GatewayGuardAdmission(ReactiveRateLimiter rateLimiter, ReactiveConcurrencyLimiter concurrencyLimiter,
        GatewayErrorWriter errorWriter, GatewayMetrics gatewayMetrics) {
        this(rateLimiter, concurrencyLimiter, errorWriter, gatewayMetrics, Mono::empty);
    }

    private GatewayGuardAdmission(ReactiveRateLimiter rateLimiter, ReactiveConcurrencyLimiter concurrencyLimiter,
        GatewayErrorWriter errorWriter, GatewayMetrics gatewayMetrics, Supplier<Mono<Void>> ownershipHandoffGate) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "Rate Limiter 不可为空");
        this.concurrencyLimiter = Objects.requireNonNull(concurrencyLimiter, "Concurrency Limiter 不可为空");
        this.errorWriter = Objects.requireNonNull(errorWriter, "Gateway 错误写入器不可为空");
        this.gatewayMetrics = Objects.requireNonNull(gatewayMetrics, "Gateway Metrics 不可为空");
        this.ownershipHandoffGate = Objects.requireNonNull(ownershipHandoffGate, "Concurrency 所有权交接门不可为空");
    }

    static GatewayGuardAdmission withOwnershipHandoffGate(ReactiveRateLimiter rateLimiter,
        ReactiveConcurrencyLimiter concurrencyLimiter, GatewayErrorWriter errorWriter, GatewayMetrics gatewayMetrics,
        Supplier<Mono<Void>> ownershipHandoffGate) {
        return new GatewayGuardAdmission(rateLimiter, concurrencyLimiter, errorWriter, gatewayMetrics,
            ownershipHandoffGate);
    }

    public Mono<Void> admit(ServerWebExchange exchange, List<RateLimitCommand> rateCommands,
        List<ConcurrencyCommand> concurrencyCommands, String lifecycleAttribute, GuardPhase phase,
        Supplier<Mono<Void>> admittedChain) {
        Objects.requireNonNull(exchange, "ServerWebExchange 不可为空");
        List<RateLimitCommand> immutableRateCommands = List.copyOf(rateCommands);
        List<ConcurrencyCommand> immutableConcurrencyCommands = List.copyOf(concurrencyCommands);
        Objects.requireNonNull(lifecycleAttribute, "Guard Lifecycle 属性不可为空");
        Objects.requireNonNull(phase, "Guard Phase 不可为空");
        Objects.requireNonNull(admittedChain, "Guard 后续链不可为空");
        return Mono.defer(() -> rateLimiter.evaluate(immutableRateCommands))
            .onErrorMap(GuardDependencyUnavailableException.class, GuardAdmissionDependencyException::new)
            .flatMap(decision -> afterRate(exchange, immutableConcurrencyCommands, lifecycleAttribute, phase,
                admittedChain, decision))
            .onErrorResume(GuardAdmissionDependencyException.class, exception -> writeDependencyUnavailable(exchange));
    }

    private Mono<Void> afterRate(ServerWebExchange exchange, List<ConcurrencyCommand> concurrencyCommands,
        String lifecycleAttribute, GuardPhase phase, Supplier<Mono<Void>> admittedChain, RateLimitDecision decision) {
        if (!decision.allowed()) {
            gatewayMetrics.recordRateLimitRejection(runtimeRoute(exchange),
                decision.rejectingDimension().orElseThrow());
            return writeRateLimited(exchange, decision.retryAfterSeconds());
        }
        return admitConcurrency(exchange, concurrencyCommands, lifecycleAttribute, phase, admittedChain);
    }

    private Mono<Void> admitConcurrency(ServerWebExchange exchange, List<ConcurrencyCommand> commands,
        String lifecycleAttribute, GuardPhase phase, Supplier<Mono<Void>> admittedChain) {
        return Mono.defer(() -> {
            AcquireHandoff handoff = new AcquireHandoff(phase);
            Flux<ConcurrencyDecision> durableAcquire = Mono.defer(() -> concurrencyLimiter.acquire(commands))
                .onErrorMap(GuardDependencyUnavailableException.class, GuardAdmissionDependencyException::new)
                .flatMapMany(decision -> handoff.holdUntilFinished(decision)).replay(1).autoConnect(0);
            return durableAcquire.next()
                .flatMap(decision -> afterConcurrency(exchange, lifecycleAttribute, admittedChain, decision, handoff))
                .doOnCancel(handoff::cancel);
        });
    }

    private Mono<Void> afterConcurrency(ServerWebExchange exchange, String lifecycleAttribute,
        Supplier<Mono<Void>> admittedChain, ConcurrencyDecision decision, AcquireHandoff handoff) {
        if (!decision.allowed()) {
            handoff.finishWithoutLease();
            gatewayMetrics.recordConcurrencyLimitRejection(runtimeRoute(exchange),
                decision.rejectingDimension().orElseThrow());
            return writeConcurrencyLimited(exchange, decision.retryAfterSeconds());
        }
        return Mono.usingWhen(handoff.claimLease(), lifecycle -> {
            if (!handoff.transferToAdmittedChain()) {
                return Mono.empty();
            }
            exchange.getAttributes().put(lifecycleAttribute, lifecycle);
            return Mono.defer(admittedChain);
        }, lifecycle -> releaseAndFinish(handoff, lifecycle),
            (lifecycle, error) -> releaseAndFinish(handoff, lifecycle),
            lifecycle -> releaseAndFinish(handoff, lifecycle));
    }

    private Mono<Void> releaseAndFinish(AcquireHandoff handoff, LeaseLifecycle lifecycle) {
        return safeRelease(lifecycle).doFinally(ignored -> handoff.finishAdmittedChain());
    }

    private Mono<Void> safeRelease(LeaseLifecycle lifecycle) {
        if (!lifecycle.released.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return concurrencyLimiter.release(lifecycle.lease).onErrorResume(error -> {
            gatewayMetrics.recordConcurrencyReleaseFailure(lifecycle.phase);
            LOG.warn("Gateway Guard concurrency lease release failed");
            return Mono.empty();
        });
    }

    private Mono<Void> writeRateLimited(ServerWebExchange exchange, long retryAfterSeconds) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.RATE_LIMITED);
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCode.GATEWAY_RATE_LIMITED,
            retryAfterSeconds);
    }

    private Mono<Void> writeConcurrencyLimited(ServerWebExchange exchange, long retryAfterSeconds) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.CONCURRENCY_LIMITED);
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCode.GATEWAY_CONCURRENCY_LIMITED,
            retryAfterSeconds);
    }

    private GatewayRuntimeRoute runtimeRoute(ServerWebExchange exchange) {
        return GatewayExchangeAttributes.runtimeRoute(exchange)
            .orElseThrow(() -> new IllegalStateException("Guard 拒绝缺少请求固定 Route"));
    }

    Mono<Void> writeDependencyUnavailable(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.DEPENDENCY_UNAVAILABLE);
        return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
            GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, null);
    }

    private static final class LeaseLifecycle {
        private final ConcurrencyLease lease;
        private final GuardPhase phase;
        private final AtomicBoolean released = new AtomicBoolean();

        private LeaseLifecycle(ConcurrencyLease lease, GuardPhase phase) {
            this.lease = lease;
            this.phase = phase;
        }
    }

    private static final class GuardAdmissionDependencyException extends RuntimeException {
        private GuardAdmissionDependencyException(GuardDependencyUnavailableException cause) {
            super(cause);
        }
    }

    private final class AcquireHandoff {
        private final GuardPhase phase;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final Sinks.Empty<Void> termination = Sinks.empty();
        private volatile LeaseLifecycle lifecycle;

        private AcquireHandoff(GuardPhase phase) {
            this.phase = phase;
        }

        private Flux<ConcurrencyDecision> holdUntilFinished(ConcurrencyDecision decision) {
            receive(decision);
            return Mono.just(decision).concatWith(termination.asMono().then(Mono.defer(this::releaseOrphanedLease))
                .then(Mono.<ConcurrencyDecision>empty()));
        }

        private Mono<LeaseLifecycle> claimLease() {
            return Mono.defer(ownershipHandoffGate).doOnError(ignored -> cancel())
                .then(Mono.fromSupplier(() -> Objects.requireNonNull(lifecycle, "已允许的并发 Lease 不可为空")));
        }

        private void finishWithoutLease() {
            finish();
        }

        private boolean transferToAdmittedChain() {
            return !cancelled.get();
        }

        private void finishAdmittedChain() {
            finish();
        }

        private void receive(ConcurrencyDecision decision) {
            if (decision.allowed()) {
                lifecycle = new LeaseLifecycle(decision.lease().orElseThrow(), phase);
            }
        }

        private void cancel() {
            cancelled.set(true);
            finish();
        }

        private Mono<Void> releaseOrphanedLease() {
            LeaseLifecycle acquired = lifecycle;
            return acquired == null ? Mono.empty() : safeRelease(acquired);
        }

        private void finish() {
            termination.tryEmitEmpty();
        }
    }
}
