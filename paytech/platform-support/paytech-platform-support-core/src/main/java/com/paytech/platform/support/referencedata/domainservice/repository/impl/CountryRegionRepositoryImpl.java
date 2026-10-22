package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CountryRegion;
import com.paytech.platform.support.referencedata.core.dal.mapper.CountryRegionMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.CountryRegionRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的国家区域映射仓储。 */
@Repository
@AllArgsConstructor
public class CountryRegionRepositoryImpl implements CountryRegionRepository {

    private final CountryRegionMapper mapper;

    /** 全部映射。 */
    @Override
    public List<CountryRegion> findAll() {
        return mapper.selectAll();
    }
}
