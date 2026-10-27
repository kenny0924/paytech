package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCountry;
import com.paytech.platform.support.referencedata.core.dal.mapper.ReferenceCountryMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.ReferenceCountryRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的国家仓储。 */
@Repository
@AllArgsConstructor
public class ReferenceCountryRepositoryImpl implements ReferenceCountryRepository {

    private final ReferenceCountryMapper mapper;

    /** 按条件查询。 */
    @Override
    public List<ReferenceCountry> find(ReferenceDataStatus status, List<String> codes) {
        return mapper.selectByFilter(status, codes);
    }
}
