package com.paytech.platform.support.integration.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationLookupService;

import lombok.AllArgsConstructor;

/** 按编码读取接入方主表判断存在性；单条读取无需一致性快照，因此不开事务。 */
@Service
@AllArgsConstructor
public class IntegrationLookupServiceImpl implements IntegrationLookupService {

    private final IntegrationRepository integrations;

    /** 复用按编码读取，只回答是否存在。 */
    @Override
    public boolean exists(String integrationCode) {
        return integrations.byCode(integrationCode) != null;
    }
}
