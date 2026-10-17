package com.paytech.foundation.web.request;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Establishes a safe request identifier for response correlation and MDC.
 *
 * @author Codex
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = ApiProblemProfile.REQUEST_ID_HEADER;
    public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    public static final String MDC_KEY = ApiProblemProfile.REQUEST_ID_MDC_KEY;

    private final Supplier<String> requestIdSupplier;

    public RequestIdFilter() {
        this(() -> UUID.randomUUID().toString());
    }

    public RequestIdFilter(Supplier<String> requestIdSupplier) {
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestId = resolveRequestId(Collections.list(request.getHeaders(HEADER_NAME)));
        String previousRequestId = MDC.get(MDC_KEY);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc(previousRequestId);
        }
    }

    private String resolveRequestId(List<String> candidates) {
        if (candidates.size() == 1 && ApiProblemProfile.isValidRequestId(candidates.getFirst())) {
            return candidates.getFirst();
        }
        String generatedRequestId = requestIdSupplier.get();
        if (!ApiProblemProfile.isValidRequestId(generatedRequestId)) {
            throw new IllegalStateException("Generated request ID does not match the Problem Profile");
        }
        return generatedRequestId;
    }

    private void restoreMdc(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(MDC_KEY);
            return;
        }
        MDC.put(MDC_KEY, previousRequestId);
    }
}
