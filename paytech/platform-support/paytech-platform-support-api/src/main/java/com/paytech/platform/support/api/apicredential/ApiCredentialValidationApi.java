package com.paytech.platform.support.api.apicredential;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;

import reactor.core.publisher.Mono;

/** PLT-I03 Client API Credential 私网校验合同。 */
@HttpExchange("/api-credentials/v1")
public interface ApiCredentialValidationApi {

    /**
     * 校验 API 凭证并返回当前可信 Client 身份。
     *
     * @param credential 单值 Header 中的裸 opaque Credential，不带 Bearer 前缀；缺失或无效返回 401
     * @return 可信租户、Client、凭证与有效 Scope；数据异常或校验不可用返回 503
     *         <li>无请求 Body，不接受调用方选择的身份或 Scope。</li>
     *         <li>保留 Mono 合同，供 Gateway 使用非阻塞 HTTP 客户端。</li>
     */
    @PostExchange("/validate")
    Mono<ValidatedCredentialResponseBody> validate(
        @RequestHeader(value = ApiCredentialConstraints.CREDENTIAL_HEADER, required = false) String credential);
}
