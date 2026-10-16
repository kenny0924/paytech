package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleLine;

/** t_accounting_rule_line：规则行随版本一次写入，之后只读。 */
public interface AccountingRuleLineRepository {

    /** 版本全部规则行，按指令类型、行号升序。 */
    List<AccountingRuleLine> findByVersion(long bundleVerId);

    /** 批量写入一个版本的规则行。 */
    void insertAll(List<AccountingRuleLine> lines);
}
