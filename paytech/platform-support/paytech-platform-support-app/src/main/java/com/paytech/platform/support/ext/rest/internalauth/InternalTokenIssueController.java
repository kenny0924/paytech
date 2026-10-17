package com.paytech.platform.support.ext.rest.internalauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.internalauth.InternalTokenIssueApi;
import com.paytech.platform.support.api.internalauth.request.InternalTokenIssueRequestBody;
import com.paytech.platform.support.api.internalauth.response.InternalTokenIssueResponseBody;
import com.paytech.platform.support.service.processor.internalauth.IssueInternalTokenProcessor;

import lombok.AllArgsConstructor;

/**
 * 内部调用 Token 签发的协议适配；不开事务。
 * <li>调用方先经 {@link InternalCallerResolver} 确定，再交给 Processor 按白名单授权。</li>
 * <li>响应含 Token：本路径登记在 web.logging.metadata-only-path-prefixes，不记录 Body。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "internal-token.issuer.enabled", havingValue = "true")
public class InternalTokenIssueController implements InternalTokenIssueApi {

    private final InternalCallerResolver callers;
    private final InternalTokenConverter converter;
    private final IssueInternalTokenProcessor processor;

    /** 解析调用方后委托签发。 */
    @Override
    public InternalTokenIssueResponseBody issue(String callerService, InternalTokenIssueRequestBody requestBody) {
        var response = processor.process(converter.convertRequest(callers.resolve(callerService), requestBody));
        return converter.convertResponse(response);
    }
}
