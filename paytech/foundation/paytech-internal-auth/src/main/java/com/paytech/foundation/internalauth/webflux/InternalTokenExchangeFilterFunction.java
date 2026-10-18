package com.paytech.foundation.internalauth.webflux;

import java.util.Objects;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import com.paytech.foundation.internalauth.api.InternalPurpose;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * WebClient 出站：为一个目标服务注入 {@code Authorization: Bearer <token>}（供 Gateway 使用）。
 * <li>Purpose 取请求属性 {@link InternalPurpose#REQUEST_ATTRIBUTE}，缺省 {@code GENERAL}。</li>
 * <li>Token 获取可能同步访问 Platform，因此在 boundedElastic 上执行；获取失败时请求不发出，错误原样传播。</li>
 *
 * @author Claude
 */
public final class InternalTokenExchangeFilterFunction implements ExchangeFilterFunction {

    private final String targetService;
    private final InternalTokenSupplier supplier;

    /**
     * 创建过滤器。
     *
     * @param supplier Token 来源
     * @param targetService 目标服务名
     */
    public InternalTokenExchangeFilterFunction(InternalTokenSupplier supplier, String targetService) {
        this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
        this.targetService = Objects.requireNonNull(targetService, "targetService must not be null");
    }

    /**
     * 取得 Token 后改写请求头并继续。
     */
    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String purpose = request.attribute(InternalPurpose.REQUEST_ATTRIBUTE).map(String.class::cast)
            .orElse(InternalTokenClaims.GENERAL_PURPOSE);
        return Mono.fromCallable(() -> supplier.token(targetService, purpose))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(token -> next.exchange(ClientRequest.from(request)
                .headers(headers -> headers.setBearerAuth(token)).build()));
    }
}
