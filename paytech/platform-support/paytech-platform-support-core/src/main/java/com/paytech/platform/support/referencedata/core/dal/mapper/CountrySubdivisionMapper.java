package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CountrySubdivision;

/** t_ref_country_subdivision 查询。 */
public interface CountrySubdivisionMapper {

    /**
     * 按可选国家、状态与行政区代码查询。
     *
     * @param countryCode 国家代码；为空不限制
     * @param status 状态；为空不限制
     * @param codes 行政区代码；为空不限制
     * @return 按行政区代码升序
     */
    List<CountrySubdivision> selectByFilter(@Param("countryCode") String countryCode,
        @Param("status") ReferenceDataStatus status, @Param("codes") List<String> codes);
}
