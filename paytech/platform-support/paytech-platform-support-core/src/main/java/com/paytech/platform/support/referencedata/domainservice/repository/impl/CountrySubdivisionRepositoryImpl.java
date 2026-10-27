package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CountrySubdivision;
import com.paytech.platform.support.referencedata.core.dal.mapper.CountrySubdivisionMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.CountrySubdivisionRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的行政区仓储。 */
@Repository
@AllArgsConstructor
public class CountrySubdivisionRepositoryImpl implements CountrySubdivisionRepository {

    private final CountrySubdivisionMapper mapper;

    /** 按条件查询。 */
    @Override
    public List<CountrySubdivision> find(String countryCode, ReferenceDataStatus status, List<String> codes) {
        return mapper.selectByFilter(countryCode, status, codes);
    }
}
