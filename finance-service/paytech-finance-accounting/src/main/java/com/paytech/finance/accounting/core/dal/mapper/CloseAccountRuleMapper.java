package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.CloseAccountRule;

/** t_close_account_rule：收入/费用到本期损益与留存收益的结转映射读取。 */
@Mapper
public interface CloseAccountRuleMapper {

    /** 读取指定时点对某来源科目生效的结转规则。 */
    CloseAccountRule selectEffective(@Param("chartId") long chartId,
        @Param("sourceAccountId") long sourceAccountId, @Param("at") Instant at);

    /** 读取指定时点在科目表内生效的全部结转规则，按来源科目升序。 */
    List<CloseAccountRule> selectAllEffective(@Param("chartId") long chartId, @Param("at") Instant at);
}
