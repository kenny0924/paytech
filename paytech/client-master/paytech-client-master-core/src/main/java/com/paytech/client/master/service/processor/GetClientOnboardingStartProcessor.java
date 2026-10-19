package com.paytech.client.master.service.processor;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.ext.rest.ClientDraftConverter;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.GetClientOnboardingStartRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * C01 开始页导航：当前账号最近的申请摘要。
 * <li>会话在事务外核对；单次只读查询不会改变申请状态，不加锁、不开事务。</li>
 */
@AllArgsConstructor
public class GetClientOnboardingStartProcessor extends AbstractProcessor<GetClientOnboardingStartRequest, StartResponseBody> {
    private final ClientDraftService service;
    private final ClientDraftAccountClient accounts;

    @Override
    protected StartResponseBody processRequest(GetClientOnboardingStartRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        return ClientDraftConverter.start(service.recentApplications(request.getAccountId()));
    }
}
