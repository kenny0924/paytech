package com.paytech.gateway.guard.ratelimit;

import java.util.List;

import reactor.core.publisher.Mono;

/** 非阻塞原子多 Bucket 限流边界。 */
public interface ReactiveRateLimiter {
    Mono<RateLimitDecision> evaluate(List<RateLimitCommand> commands);
}
