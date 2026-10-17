package com.paytech.risk.ext.rest.compliance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.risk.api.compliance.ClientReviewResultApi;
import com.paytech.risk.api.compliance.response.ClientReviewResultResponseBody;
import com.paytech.risk.compliance.service.processor.GetClientReviewResultProcessor;
import com.paytech.risk.compliance.service.processor.request.ClientReviewResultRequest;

import lombok.AllArgsConstructor;

/**
 * Client BFF 读取客户可见审核结果的私网入口
 * <li>仅在 onboarding.review.enabled=true 时注册。</li>
 * <li>X-Caller-Service 暂不校验，与 I01/I02 一致，调用方鉴权待统一安全方案处理。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewResultController implements ClientReviewResultApi {
    private final GetClientReviewResultProcessor processor;

    @Override
    public ClientReviewResultResponseBody result(String caller, String applicationId) {
        var response = processor.process(
            new ClientReviewResultRequest(ClientReviewRegistrationController.positive(applicationId, "申请 ID 无效")));
        return ClientReviewWireConverter.result(response);
    }
}
