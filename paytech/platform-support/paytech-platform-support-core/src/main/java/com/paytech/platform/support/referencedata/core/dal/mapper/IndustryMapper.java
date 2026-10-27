package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Industry;

/** t_ref_industry 查询。 */
public interface IndustryMapper {

    /**
     * 按可选层级、上级大类、状态与行业编码查询。
     *
     * @param level 层级；为空不限制
     * @param parentCode 上级大类编码；为空不限制
     * @param status 状态；为空不限制
     * @param codes 行业编码；为空不限制
     * @return 按大类 sort_no、细类 sort_no 排序，每个大类之后紧跟其细类
     */
    List<Industry> selectByFilter(@Param("level") Integer level, @Param("parentCode") String parentCode,
        @Param("status") ReferenceDataStatus status, @Param("codes") List<String> codes);
}
