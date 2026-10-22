package com.paytech.platform.support.referencedata.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CountryRegion;

/** 国家区域映射单表仓储。 */
public interface CountryRegionRepository {

    /** 全部国家区域映射，按国家代码升序。 */
    List<CountryRegion> findAll();
}
