package com.paytech.client.master.domainservice.service.impl;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.ClientClassificationContext;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationSourceService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 普通已提交读取；读取后申请或分类变化由 Risk 侧按任务状态拒绝。 */
@AllArgsConstructor
public class ClientClassificationSourceServiceImpl implements ClientClassificationSourceService {
    private final ClientBusinessProfileRepository profiles;
    private final ClientApplicationRepository applications;
    private final ClientBusinessClassificationRepository classifications;

    /** 当前任务、确认归属与分类行。 */
    @Override
    public ClientClassificationContext deliverySource(long caseId) {
        ClientApplicationSubmission application = applications.findByCase(caseId);
        if (application == null || application.getStatus() != ClientApplicationStatus.UNDER_REVIEW) {
            return null;
        }
        ClientClassificationProfile profile = profiles.findClassificationProfile(application.getClientId());
        if (profile == null || !profile.confirmedFor(caseId)) {
            throw new ErrorException("已登记交付的分类未确认: case=" + caseId);
        }
        return new ClientClassificationContext(caseId, application, profile,
            classifications.findByProfile(profile.getBusinessProfileId()), null, null);
    }
}
