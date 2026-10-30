package com.paytech.client.master.domainservice.service.impl;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.core.dal.dataobject.ClientClassification;
import com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile;
import com.paytech.client.master.domain.classification.ClientBusinessClassificationView;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.service.ClientBusinessClassificationQueryService;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * 读取 Client、经营资料的分类确认与主分类
 * <li>三次读取共用一个只读可重复读事务：确认状态与分类行须来自同一快照，否则重新绑定任务并替换分类的提交可能被读成"已确认的新分类"。</li>
 */
@AllArgsConstructor
public class ClientBusinessClassificationQueryServiceImpl implements ClientBusinessClassificationQueryService {
    private final TransactionTemplate reads;
    private final ClientRepository clientRepository;
    private final ClientBusinessProfileRepository clientBusinessProfileRepository;
    private final ClientBusinessClassificationRepository clientBusinessClassificationRepository;

    /** 在一致快照内读取；未确认时不返回分类码。 */
    @Override
    public ClientBusinessClassificationView getBusinessClassification(long clientId) {
        return reads.execute(status -> read(clientId));
    }

    /** Client 必须存在；没有经营资料或未确认按未确认返回。 */
    ClientBusinessClassificationView read(long clientId) {
        if (clientRepository.findById(clientId) == null) {
            throw new FailException(CheckoutCandidateErrorCode.CLIENT_NOT_FOUND);
        }
        ClientClassificationProfile profile = clientBusinessProfileRepository.findClassificationProfile(clientId);
        if (profile == null || profile.getClassificationConfirmedTime() == null) {
            return new ClientBusinessClassificationView(clientId, false, null, null);
        }
        ClientClassification primary = clientBusinessClassificationRepository.findByProfile(profile.getBusinessProfileId())
            .stream().filter(ClientClassification::isPrimary).findFirst().orElse(null);
        return primary == null ? new ClientBusinessClassificationView(clientId, true, null, null)
            : new ClientBusinessClassificationView(clientId, true, primary.getIndustryCode(), primary.getMccCode());
    }
}
