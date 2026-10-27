package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import com.paytech.client.master.core.dal.mapper.AttributeDefinitionMapper;
import com.paytech.client.master.domainservice.repository.AttributeDefinitionRepository;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 AttributeDefinitionMapper 访问本表。 */
@AllArgsConstructor
public class AttributeDefinitionRepositoryImpl implements AttributeDefinitionRepository {
    private final AttributeDefinitionMapper mapper;

    /** 辖区与全局定义。 */
    @Override
    public List<AttributeDefinition> findForJurisdiction(String jurisdictionCode) {
        return List.copyOf(mapper.findForJurisdiction(jurisdictionCode));
    }
}
