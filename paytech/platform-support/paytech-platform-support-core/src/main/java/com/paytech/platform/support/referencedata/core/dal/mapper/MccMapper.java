package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Mcc;

/** t_ref_mcc 查询。 */
public interface MccMapper {

    /**
     * 按条件分页查询。
     *
     * @param keywordPattern 已按 ! 转义的关键字，匹配编码前缀或英文名包含；为空不限制
     * @param status 状态；为空不限制
     * @param codes MCC 编码；为空不限制
     * @param offset 偏移
     * @param limit 条数
     * @return 按 MCC 编码升序的一页
     */
    List<Mcc> search(@Param("keywordPattern") String keywordPattern, @Param("status") ReferenceDataStatus status,
        @Param("codes") List<String> codes, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 与分页查询相同条件的总数。
     *
     * @param keywordPattern 已按 ! 转义的关键字；为空不限制
     * @param status 状态；为空不限制
     * @param codes MCC 编码；为空不限制
     * @return 总数
     */
    long count(@Param("keywordPattern") String keywordPattern, @Param("status") ReferenceDataStatus status,
        @Param("codes") List<String> codes);
}
