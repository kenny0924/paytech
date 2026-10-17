package com.paytech.gateway.web;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLException;

import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.resolver.dns.DnsNameResolverException;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.internal.shaded.reactor.pool.PoolAcquirePendingLimitException;
import reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException;
import reactor.netty.internal.shaded.reactor.pool.PoolShutdownException;

/** 对 resolved transport stack 做深度有限、identity-safe 的唯一失败分类。 */
@Component
public final class GatewayUpstreamFailureClassifier {
    private static final int MAX_CAUSE_NODES = 32;

    public Optional<Failure> classify(Throwable throwable) {
        List<Throwable> causes = finiteCauses(throwable);
        Optional<Failure> application = firstApplicationFailure(causes);
        if (application.isPresent()) {
            return application;
        }
        if (causes.stream().anyMatch(this::isProtocolFailure)) {
            return Optional.of(Failure.INVALID_RESPONSE);
        }
        if (causes.stream().anyMatch(this::isDependencyFailure)) {
            return Optional.of(Failure.DEPENDENCY_UNAVAILABLE);
        }
        if (causes.stream().anyMatch(this::isResponseTimeout)) {
            return Optional.of(Failure.UPSTREAM_TIMEOUT);
        }
        return Optional.empty();
    }

    private List<Throwable> finiteCauses(Throwable throwable) {
        List<Throwable> causes = new ArrayList<>(MAX_CAUSE_NODES);
        IdentityHashMap<Throwable, Boolean> visited = new IdentityHashMap<>();
        Throwable current = throwable;
        while (current != null && causes.size() < MAX_CAUSE_NODES && visited.put(current, Boolean.TRUE) == null) {
            causes.add(current);
            current = current.getCause();
        }
        return causes;
    }

    private Optional<Failure> firstApplicationFailure(List<Throwable> causes) {
        if (causes.stream().anyMatch(cause -> cause instanceof InvalidUpstreamResponseException
            || cause instanceof GatewayUpstreamProtocolException)) {
            return Optional.of(Failure.INVALID_RESPONSE);
        }
        if (causes.stream().anyMatch(GatewayUpstreamTimeoutException.class::isInstance)) {
            return Optional.of(Failure.UPSTREAM_TIMEOUT);
        }
        return Optional.empty();
    }

    private boolean isProtocolFailure(Throwable cause) {
        return cause instanceof SSLException || cause instanceof PrematureCloseException
            || cause.getClass() == SocketException.class || cause instanceof ContentLengthNotAllowedException
            || cause instanceof InvalidChunkExtensionException || cause instanceof InvalidChunkTerminationException
            || cause instanceof InvalidLineSeparatorException || cause instanceof TooLongHttpHeaderException
            || cause instanceof TooLongHttpLineException || cause instanceof TransferEncodingNotAllowedException;
    }

    private boolean isDependencyFailure(Throwable cause) {
        return cause instanceof ConnectException || cause instanceof UnknownHostException
            || cause instanceof DnsNameResolverException || cause instanceof PoolAcquireTimeoutException
            || cause instanceof PoolAcquirePendingLimitException || cause instanceof PoolShutdownException;
    }

    private boolean isResponseTimeout(Throwable cause) {
        return cause instanceof TimeoutException || cause instanceof ReadTimeoutException;
    }

    /** 三种稳定的 BFF 失败语义。 */
    public enum Failure {
        INVALID_RESPONSE,

        DEPENDENCY_UNAVAILABLE,

        UPSTREAM_TIMEOUT
    }
}
