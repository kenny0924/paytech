package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.mapper.IdentifierTypeMapper;
import com.paytech.client.master.domainservice.repository.IdentifierTypeRepository;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 IdentifierTypeMapper 访问本表。 */
@AllArgsConstructor
public class IdentifierTypeRepositoryImpl implements IdentifierTypeRepository {
    private final IdentifierTypeMapper mapper;

    /** 辖区与全局类型。 */
    @Override
    public List<IdentifierType> findForJurisdiction(String jurisdictionCode) {
        return List.copyOf(mapper.findForJurisdiction(jurisdictionCode));
    }

    /** 按代码读取。 */
    @Override
    public IdentifierType findByCode(String code) {
        return code == null ? null : mapper.findByCode(code);
    }
}
