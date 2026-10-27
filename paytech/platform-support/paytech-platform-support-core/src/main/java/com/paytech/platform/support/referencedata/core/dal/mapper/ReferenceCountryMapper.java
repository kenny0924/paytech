package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCountry;

/** t_ref_country 查询。 */
public interface ReferenceCountryMapper {

    /**
     * 按可选状态与国家代码查询。
     *
     * @param status 状态；为空不限制
     * @param codes 国家代码；为空不限制
     * @return 按国家代码升序
     */
    List<ReferenceCountry> selectByFilter(@Param("status") ReferenceDataStatus status,
        @Param("codes") List<String> codes);
}
