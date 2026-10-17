package com.paytech.foundation.internalauth.contract;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Platform 内部 Token 签发接口合同（端点在第 02a 步实现，后续由 Platform {@code -api} 引用本合同）。
 * <li>调用该接口的客户端自身不注入内部 Token。</li>
 *
 * @author Claude
 */
@HttpExchange("/platform/v1/internal-tokens")
public interface InternalTokenIssueApi {

    /**
     * 为调用方签发访问目标服务的短期 Token。
     *
     * @param request 目标服务与 Purpose
     * @return Token 与过期时间
     */
    @PostExchange
    InternalTokenIssueResponse issue(@RequestBody InternalTokenIssueRequest request);
}
