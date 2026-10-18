package com.paytech.gateway.guard;

import java.util.function.Supplier;

import com.paytech.gateway.guard.concurrency.ReactiveConcurrencyLimiter;
import com.paytech.gateway.guard.ratelimit.ReactiveRateLimiter;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.web.GatewayErrorWriter;

import reactor.core.publisher.Mono;

/** Operations Guard 所有权交接竞态的测试入口。 */
public final class OperationsGuardAdmissionTestSupport {
    private OperationsGuardAdmissionTestSupport() {}

    public static GatewayGuardAdmission withOwnershipHandoffGate(ReactiveRateLimiter rateLimiter,
        ReactiveConcurrencyLimiter concurrencyLimiter, GatewayErrorWriter errorWriter, GatewayMetrics gatewayMetrics,
        Supplier<Mono<Void>> ownershipHandoffGate) {
        return GatewayGuardAdmission.withOwnershipHandoffGate(rateLimiter, concurrencyLimiter, errorWriter,
            gatewayMetrics, ownershipHandoffGate);
    }
}
