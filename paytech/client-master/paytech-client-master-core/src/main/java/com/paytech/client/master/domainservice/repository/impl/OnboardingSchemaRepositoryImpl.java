package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.core.dal.mapper.OnboardingSchemaMapper;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaRepository;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 OnboardingSchemaMapper 访问本表。 */
@AllArgsConstructor
public class OnboardingSchemaRepositoryImpl implements OnboardingSchemaRepository {
    private final OnboardingSchemaMapper mapper;

    /** 精确键的 ACTIVE 版本。 */
    @Override
    public OnboardingSchema findActive(String jurisdictionCode, String partyType, String roleType, String organizationTypeCode) {
        return mapper.findActive(jurisdictionCode, partyType, roleType, organizationTypeCode);
    }

    /** 按 ID 读取。 */
    @Override
    public OnboardingSchema findById(long schemaId) {
        return mapper.findById(schemaId);
    }

    /** 辖区全部版本。 */
    @Override
    public List<OnboardingSchema> findByJurisdiction(String jurisdictionCode) {
        return List.copyOf(mapper.findByJurisdiction(jurisdictionCode));
    }
}
