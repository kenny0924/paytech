package com.paytech.gateway.guard.concurrency;

import java.util.List;

import reactor.core.publisher.Mono;

/** 非阻塞原子多维并发 Lease 边界。 */
public interface ReactiveConcurrencyLimiter {
    Mono<ConcurrencyDecision> acquire(List<ConcurrencyCommand> commands);

    Mono<Void> release(ConcurrencyLease lease);
}
