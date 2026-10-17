package com.paytech.client.master.ext.rest;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.onboarding.ClientApplicationAdminApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.master.service.processor.GetClientReviewApplicationProcessor;
import com.paytech.client.master.service.processor.request.ClientClassificationRequest;

import lombok.AllArgsConstructor;

/**
 * O02 申请资料读取的私网协议适配；操作者校验与分类接口一致
 * <li>与草稿、补偿开关同时开启时注册。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientApplicationAdminController implements ClientApplicationAdminApi {
    private final GetClientReviewApplicationProcessor processor;

    /** 委托只读用例。 */
    @Override
    public ClientDraftResponseBody application(String actorAccountId, String sessionId, String caseId) {
        long actor = ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return processor.process(new ClientClassificationRequest(ClientClassificationAdminController.id(caseId), actor, List.of()));
    }
}
