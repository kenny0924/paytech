package com.paytech.finance.accounting.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.ChartOfAccounts;

/** t_chart_of_accounts：科目表只读访问。 */
@Mapper
public interface ChartOfAccountsMapper {

    /** 按科目表代码读取。 */
    ChartOfAccounts selectByChartCode(@Param("chartCode") String chartCode);
}
