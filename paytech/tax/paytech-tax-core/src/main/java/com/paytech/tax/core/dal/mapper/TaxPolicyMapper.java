package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxPolicy;

/** t_tax_policy：地区包读取。 */
@Mapper
public interface TaxPolicyMapper {
    /** 按 ID 读取；不存在返回 null。 */
    TaxPolicy selectById(@Param("id") long id);

    /** 按 ID 批量读取。 */
    List<TaxPolicy> selectByIds(@Param("ids") Collection<Long> ids);
}
