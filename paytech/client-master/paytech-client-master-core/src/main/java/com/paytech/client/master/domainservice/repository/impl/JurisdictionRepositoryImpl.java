package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.mapper.JurisdictionMapper;
import com.paytech.client.master.domainservice.repository.JurisdictionRepository;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 JurisdictionMapper 访问本表。 */
@AllArgsConstructor
public class JurisdictionRepositoryImpl implements JurisdictionRepository {
    private final JurisdictionMapper mapper;

    /** 按代码读取。 */
    @Override
    public Jurisdiction find(String jurisdictionCode) {
        return jurisdictionCode == null ? null : mapper.find(jurisdictionCode);
    }

    /** 全部辖区包。 */
    @Override
    public List<Jurisdiction> findAll() {
        return List.copyOf(mapper.findAll());
    }
}
