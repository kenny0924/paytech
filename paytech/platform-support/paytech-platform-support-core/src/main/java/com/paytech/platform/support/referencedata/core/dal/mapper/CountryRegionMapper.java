package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.util.List;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CountryRegion;

/** t_country_region 查询。 */
public interface CountryRegionMapper {

    /**
     * 查询全部国家区域映射。
     *
     * @return 按国家代码升序
     */
    List<CountryRegion> selectAll();
}
