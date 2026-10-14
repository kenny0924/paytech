package com.paytech.client.contract.service.processor;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.contract.core.dal.dataobject.ClientClassificationProfile;
import com.paytech.client.contract.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.contract.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.contract.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.contract.service.processor.request.ClientClassificationRequest;
import com.paytech.client.contract.service.processor.response.ClientClassificationResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

import lombok.AllArgsConstructor;

/**
 * O02 分类部分：读取审核任务当前分类；只读，不加锁、不开事务
 * <li>任务必须是某申请的当前任务；分类尚未绑定本任务时返回空集合。</li>
 */
@AllArgsConstructor
public class GetClientClassificationProcessor extends AbstractProcessor<ClientClassificationRequest, ClientClassificationResponse> {
    private final ClientBusinessProfileRepository profiles;
    private final ClientApplicationRepository applications;
    private final ClientBusinessClassificationRepository classifications;

    @Override
    protected ClientClassificationResponse processRequest(ClientClassificationRequest request) {
        ClientApplicationSubmission application = currentApplication(applications, request.getCaseId());
        ClientClassificationProfile profile = classificationProfile(profiles, application);
        return response(request.getCaseId(), application, profile, classifications);
    }

    /** 当前任务对应的申请；不存在时 404。 */
    static ClientApplicationSubmission currentApplication(ClientApplicationRepository applications, long caseId) {
        ClientApplicationSubmission application = applications.findByCase(caseId);
        if (application == null) {
            throw new FailException(REVIEW_CASE_NOT_FOUND);
        }
        return application;
    }

    /** 申请 Client 唯一经营资料的分类归属；缺失属于资料关联损坏。 */
    static ClientClassificationProfile classificationProfile(ClientBusinessProfileRepository profiles,
        ClientApplicationSubmission application) {
        ClientClassificationProfile profile = profiles.findClassificationProfile(application.getClientId());
        if (profile == null) {
            throw new ErrorException(DRAFT_CONTEXT_INVALID);
        }
        return profile;
    }

    /** 分类只在绑定本任务时返回。 */
    static ClientClassificationResponse response(long caseId, ClientApplicationSubmission application,
        ClientClassificationProfile profile, ClientBusinessClassificationRepository classifications) {
        boolean bound = Long.valueOf(caseId).equals(profile.getClassificationCaseId());
        return new ClientClassificationResponse(caseId, application.getApplicationId(), profile,
            bound ? classifications.findByProfile(profile.getBusinessProfileId()) : List.of());
    }
}
