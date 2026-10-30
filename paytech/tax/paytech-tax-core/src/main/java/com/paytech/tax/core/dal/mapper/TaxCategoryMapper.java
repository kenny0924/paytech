package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxCategory;

/** t_tax_category：税类目录读取。 */
@Mapper
public interface TaxCategoryMapper {
    /** 按税类代码批量读取（不过滤状态）。 */
    List<TaxCategory> selectByCodes(@Param("codes") Collection<String> codes);
}
