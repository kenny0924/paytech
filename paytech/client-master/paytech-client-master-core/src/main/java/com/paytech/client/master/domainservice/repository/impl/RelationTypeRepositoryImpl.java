package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.RelationType;
import com.paytech.client.master.core.dal.mapper.RelationTypeMapper;
import com.paytech.client.master.domainservice.repository.RelationTypeRepository;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 RelationTypeMapper 访问本表。 */
@AllArgsConstructor
public class RelationTypeRepositoryImpl implements RelationTypeRepository {
    private final RelationTypeMapper mapper;

    /** 辖区与全局类型。 */
    @Override
    public List<RelationType> findForJurisdiction(String jurisdictionCode) {
        return List.copyOf(mapper.findForJurisdiction(jurisdictionCode));
    }

    /** 全部类型。 */
    @Override
    public List<RelationType> findAll() {
        return List.copyOf(mapper.findAll());
    }
}
