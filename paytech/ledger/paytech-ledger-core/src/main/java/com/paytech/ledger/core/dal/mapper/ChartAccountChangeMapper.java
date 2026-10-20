package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.domain.view.ChartAccountChangeView;

/** t_chart_account_change：科目变更记录的追加与分页读取。 */
@Mapper
public interface ChartAccountChangeMapper {

    /** 插入一行。 */
    int insert(ChartAccountChange record);

    /** 分页读取科目表（可限定科目）的变更记录与上级科目编号，按变更时间、主键倒序。 */
    List<ChartAccountChangeView> selectPage(@Param("chartId") long chartId, @Param("chartAccountId") Long chartAccountId,
        @Param("size") int size, @Param("offset") int offset);

    /** 统计符合条件的变更记录数。 */
    long countPage(@Param("chartId") long chartId, @Param("chartAccountId") Long chartAccountId);
}
