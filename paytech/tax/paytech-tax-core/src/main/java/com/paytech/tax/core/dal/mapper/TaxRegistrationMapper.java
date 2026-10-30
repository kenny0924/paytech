package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxRegistration;

/** t_tax_registration：注册读取。 */
@Mapper
public interface TaxRegistrationMapper {
    /** 按 ID 读取；不存在返回 null。 */
    TaxRegistration selectById(@Param("id") long id);

    /** 按 ID 批量读取。 */
    List<TaxRegistration> selectByIds(@Param("ids") Collection<Long> ids);
}
