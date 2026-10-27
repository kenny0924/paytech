package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import com.paytech.client.master.core.dal.mapper.OrganizationTypeMapper;
import com.paytech.client.master.domainservice.repository.OrganizationTypeRepository;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 OrganizationTypeMapper 访问本表。 */
@AllArgsConstructor
public class OrganizationTypeRepositoryImpl implements OrganizationTypeRepository {
    private final OrganizationTypeMapper mapper;

    /** 按辖区读取。 */
    @Override
    public List<OrganizationType> findByJurisdiction(String jurisdictionCode) {
        return List.copyOf(mapper.findByJurisdiction(jurisdictionCode));
    }
}
