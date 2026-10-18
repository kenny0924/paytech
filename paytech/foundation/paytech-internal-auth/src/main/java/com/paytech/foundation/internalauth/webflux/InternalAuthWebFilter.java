package com.paytech.foundation.internalauth.webflux;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * WebFlux 入站：与 Servlet 过滤器相同的 OFF / DUAL / REQUIRED 判定（供 Gateway 使用）。
 * <li>识别出的调用方放入交换属性 {@link CallerIdentityHolder#REQUEST_ATTRIBUTE}，不使用线程绑定。</li>
 * <li>拒绝时写出 RFC 9457 Code-MoR Problem：{@code type=about:blank}，{@code code} 为内部凭证错误码。</li>
 * <li>判定可能刷新 JWKS（阻塞 IO），因此在 boundedElastic 上执行。</li>
 *
 * @author Claude
 */
public final class InternalAuthWebFilter implements WebFilter {

    private final Clock clock;
    private final InternalCallerAuthenticator authenticator;

    /**
     * 创建过滤器。
     *
     * @param authenticator 入站判定器
     * @param clock 错误时间戳时钟
     */
    public InternalAuthWebFilter(InternalCallerAuthenticator authenticator, Clock clock) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 判定后放行或拒绝。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        List<String> authorization = headers.getOrEmpty(InternalCallerAuthenticator.AUTHORIZATION_HEADER);
        List<String> legacyCaller = headers.getOrEmpty(InternalCallerAuthenticator.LEGACY_CALLER_HEADER);
        return Mono.fromCallable(() -> authenticator.authenticate(path, authorization, legacyCaller))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(decision -> {
                if (decision.rejected()) {
                    return reject(exchange, decision.rejection(), decision.status());
                }
                if (decision.identity() != null) {
                    exchange.getAttributes().put(CallerIdentityHolder.REQUEST_ATTRIBUTE, decision.identity());
                }
                return chain.filter(exchange);
            });
    }

    private Mono<Void> reject(ServerWebExchange exchange, CodeMessageEnum code, int status) {
        ServerHttpResponse response = exchange.getResponse();
        String requestId = requestId(exchange);
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        response.setStatusCode(httpStatus);
        response.getHeaders().set(ApiProblemProfile.REQUEST_ID_HEADER, requestId);
        response.getHeaders().setContentType(MediaType.parseMediaType(ApiProblemProfile.CONTENT_TYPE));
        byte[] body = problemJson(httpStatus, code, requestId).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private static String requestId(ServerWebExchange exchange) {
        List<String> values = exchange.getRequest().getHeaders().getOrEmpty(ApiProblemProfile.REQUEST_ID_HEADER);
        if (values.size() == 1 && ApiProblemProfile.isValidRequestId(values.getFirst())) {
            return values.getFirst();
        }
        return UUID.randomUUID().toString();
    }

    private String problemJson(HttpStatus status, CodeMessageEnum code, String requestId) {
        return "{\"type\":\"" + ApiProblemProfile.TYPE + "\",\"title\":\"" + json(status.getReasonPhrase())
            + "\",\"detail\":\"" + json(code.getMessage()) + "\",\"code\":\"" + code.getCode()
            + "\",\"requestId\":\"" + requestId + "\",\"timestamp\":\""
            + ApiProblemProfile.formatTimestamp(clock.instant()) + "\"}";
    }

    private static String json(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if (character == '"' || character == '\\') {
                escaped.append('\\').append(character);
            } else if (character < 0x20) {
                escaped.append(String.format("\\u%04x", (int) character));
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
