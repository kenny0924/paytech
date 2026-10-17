package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;

/** t_accounting_rule_line：规则行持久化，只插入与读取。 */
@Mapper
public interface AccountingRuleLineMapper {

    /** 批量插入规则行。 */
    int insertBatch(@Param("lines") List<AccountingRuleLine> lines);

    /** 版本全部规则行，按指令类型、行号升序。 */
    List<AccountingRuleLine> selectByVersion(@Param("bundleVerId") long bundleVerId);
}
