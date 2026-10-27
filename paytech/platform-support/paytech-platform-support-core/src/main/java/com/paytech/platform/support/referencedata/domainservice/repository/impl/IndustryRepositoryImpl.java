package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Industry;
import com.paytech.platform.support.referencedata.core.dal.mapper.IndustryMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.IndustryRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的行业仓储。 */
@Repository
@AllArgsConstructor
public class IndustryRepositoryImpl implements IndustryRepository {

    private final IndustryMapper mapper;

    /** 按条件查询。 */
    @Override
    public List<Industry> find(Integer level, String parentCode, ReferenceDataStatus status, List<String> codes) {
        return mapper.selectByFilter(level, parentCode, status, codes);
    }
}
