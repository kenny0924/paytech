package com.paytech.platform.support.api.internalauth;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.internalauth.request.InternalTokenIssueRequestBody;
import com.paytech.platform.support.api.internalauth.response.InternalTokenIssueResponseBody;

import jakarta.validation.Valid;

/**
 * 内部调用 Token 签发契约（D26）：Platform 为已登记的内部调用方签发访问目标服务的短期 ES256 Token。
 * <li>JSON 字段与 Foundation {@code paytech-internal-auth} 的签发合同一致；调用方申请 Token 时自身不携带内部 Token。</li>
 * <li>阶段 0 以 X-Caller-Service 白名单 {@code internal-token.issuer.callers} 认证调用方：过渡信任，部署必须限制私网访问。</li>
 * <li>无 Idempotency-Key；每次调用签发新 Token，超时后可原样重试。</li>
 */
@HttpExchange(url = "/platform/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface InternalTokenIssueApi {

    /** 调用方服务名 Header。 */
    String CALLER_SERVICE_HEADER = "X-Caller-Service";

    /**
     * 为调用方签发访问目标服务的短期 Token
     *
     * @param callerService
     *            调用方服务名，即 Token {@code sub}；须在白名单中登记目标服务与 Purpose
     * @param requestBody
     *            目标服务与 Purpose
     * @return Token 与过期时间
     *         <li>200 返回新 Token，响应不缓存；有效期不超过 300 秒。</li>
     *         <li>400 表示参数非法或调用方未获准；不重试。</li>
     *         <li>503 表示签名密钥不可用；可稍后重试。</li>
     */
    @PostExchange(url = "/internal-tokens", contentType = MediaType.APPLICATION_JSON_VALUE)
    InternalTokenIssueResponseBody issue(@RequestHeader(CALLER_SERVICE_HEADER) String callerService,
        @Valid @RequestBody InternalTokenIssueRequestBody requestBody);
}
