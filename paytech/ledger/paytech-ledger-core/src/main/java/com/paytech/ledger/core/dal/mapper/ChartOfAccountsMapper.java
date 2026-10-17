package com.paytech.ledger.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;

/** t_chart_of_accounts：科目表只读访问。 */
@Mapper
public interface ChartOfAccountsMapper {

    /** 按科目表代码读取。 */
    ChartOfAccounts selectByChartCode(@Param("chartCode") String chartCode);
}
