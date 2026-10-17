package com.paytech.client.master.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ClientBusinessProfile;
import com.paytech.client.master.core.dal.mapper.ClientBusinessProfileMapper;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientBusinessProfileMapper 读取 t_client_business_profile。 */
@AllArgsConstructor
public class ClientBusinessProfileRepositoryImpl implements ClientBusinessProfileRepository {
    private final ClientBusinessProfileMapper mapper;

    /** 读取本 Client Checkout 范围内的 client_business_profile 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<ClientBusinessProfile> findByClientId(long clientId) {
        return List.copyOf(mapper.findByClientId(clientId));
    }

    /** 分类归属。 */
    @Override
    public com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile findClassificationProfile(long clientId) {
        return mapper.findClassificationProfile(clientId);
    }

    /** 条件绑定任务并同步原对象。 */
    @Override
    public void bindClassificationCase(com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile profile, long caseId,
        java.time.Instant now) {
        if (mapper.bindClassificationCase(profile, caseId, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("分类已确认或已变化");
        }
        profile.setClassificationCaseId(caseId);
        profile.setClassificationConfirmedBy(null);
        profile.setClassificationConfirmedTime(null);
        profile.setUpdateTime(now);
    }

    /** 条件确认并同步原对象。 */
    @Override
    public void confirmClassification(com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile profile, long actorId,
        java.time.Instant now) {
        if (mapper.confirmClassification(profile, actorId, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("分类已确认或已变化");
        }
        profile.setClassificationConfirmedBy(actorId);
        profile.setClassificationConfirmedTime(now);
        profile.setUpdateTime(now);
    }
}
