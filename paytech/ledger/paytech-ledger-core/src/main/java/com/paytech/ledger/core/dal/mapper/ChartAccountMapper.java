package com.paytech.ledger.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;

/** t_chart_account：会计科目只读访问。 */
@Mapper
public interface ChartAccountMapper {

    /** 按主键读取。 */
    ChartAccount selectById(@Param("id") long id);

    /** 按科目表和科目代码读取。 */
    ChartAccount selectByChartAndCode(@Param("chartId") long chartId, @Param("accountCode") String accountCode);
}
