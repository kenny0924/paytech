package com.paytech.platform.support.referencedata.domainservice.service;

import java.util.List;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CountryRegion;

/** 全平台国家区域映射查询能力。 */
public interface CountryRegionQueryService {

    /** 返回全部国家区域映射，按国家代码升序。 */
    List<CountryRegion> listCountryRegions();
}
