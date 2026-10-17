package com.paytech.client.master.service.processor;

import java.util.Map;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.ext.rest.ClientDraftConverter;
import com.paytech.client.master.service.processor.request.ClientClassificationRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * O02 申请资料部分：按审核任务读取提交后的申请资料；只读事务，不加锁
 * <li>任务必须是某申请的当前任务，否则 404。</li>
 * <li>联系方式不做 Vault Reveal：Operations 读取联系方式尚无批准的用途。</li>
 */
@AllArgsConstructor
public class GetClientReviewApplicationProcessor extends AbstractProcessor<ClientClassificationRequest, ClientDraftResponseBody> {
    private final ClientApplicationRepository applications;
    private final ClientDraftService drafts;
    private final TransactionTemplate reads;

    @Override
    protected ClientDraftResponseBody processRequest(ClientClassificationRequest request) {
        ClientApplicationSubmission application = GetClientClassificationProcessor.currentApplication(applications, request.getCaseId());
        var view = reads.execute(tx -> drafts.readForReview(application.getApplicationId()));
        return ClientDraftConverter.response(view, Map.of());
    }
}
