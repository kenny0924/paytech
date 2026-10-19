package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;

/** t_chart_account：会计科目只读访问。 */
@Mapper
public interface ChartAccountMapper {

    /** 按主键读取。 */
    ChartAccount selectById(@Param("id") long id);

    /** 按主键升序读取一组科目。 */
    List<ChartAccount> selectByIds(@Param("ids") List<Long> ids);

    /** 按科目表和科目代码读取。 */
    ChartAccount selectByChartAndCode(@Param("chartId") long chartId, @Param("accountCode") String accountCode);

    /** 读取科目表下的科目，可按是否可过账过滤，按科目代码升序。 */
    List<ChartAccount> selectByChart(@Param("chartId") long chartId, @Param("postingAllowed") Boolean postingAllowed);
}
