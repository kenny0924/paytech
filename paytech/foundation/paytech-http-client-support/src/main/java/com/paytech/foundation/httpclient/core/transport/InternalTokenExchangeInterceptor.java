package com.paytech.foundation.httpclient.core.transport;

import java.io.IOException;
import java.util.Objects;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.paytech.foundation.internalauth.api.InternalPurpose;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

/**
 * 出站内部调用身份（D26）：按目标服务名注入 {@code Authorization: Bearer <token>}。
 * <li>目标服务名取自 HTTP Service Group 名（即服务发现名），或创建客户端时显式给出。</li>
 * <li>Purpose 取接口方法 / 接口上的 {@link InternalPurpose}，缺省 {@code GENERAL}。</li>
 * <li>Token 不可用时抛出 {@code InternalTokenUnavailableException}，请求不发出（失败关闭）。</li>
 *
 * @author Claude
 */
public final class InternalTokenExchangeInterceptor implements ClientHttpRequestInterceptor {

    private final String targetService;
    private final InternalTokenSupplier supplier;

    /**
     * 创建拦截器。
     *
     * @param supplier Token 来源
     * @param targetService 目标服务名
     */
    public InternalTokenExchangeInterceptor(InternalTokenSupplier supplier, String targetService) {
        this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
        this.targetService = Objects.requireNonNull(targetService, "targetService must not be null");
    }

    /**
     * 覆盖请求中已有的 Authorization 头后继续执行。
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        Object purpose = request.getAttributes().get(InternalPurpose.REQUEST_ATTRIBUTE);
        String token = supplier.token(targetService,
            purpose instanceof String code ? code : InternalTokenClaims.GENERAL_PURPOSE);
        request.getHeaders().setBearerAuth(token);
        return execution.execute(request, body);
    }
}
