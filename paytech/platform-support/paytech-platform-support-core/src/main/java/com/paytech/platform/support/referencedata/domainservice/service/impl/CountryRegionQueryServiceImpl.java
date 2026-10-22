package com.paytech.platform.support.referencedata.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CountryRegion;
import com.paytech.platform.support.referencedata.domainservice.repository.CountryRegionRepository;
import com.paytech.platform.support.referencedata.domainservice.service.CountryRegionQueryService;

import lombok.AllArgsConstructor;

/** 国家区域映射只读查询；单条 SELECT，不开事务。 */
@Service
@AllArgsConstructor
public class CountryRegionQueryServiceImpl implements CountryRegionQueryService {

    private final CountryRegionRepository repository;

    /** 全部映射。 */
    @Override
    public List<CountryRegion> listCountryRegions() {
        return repository.findAll();
    }
}
