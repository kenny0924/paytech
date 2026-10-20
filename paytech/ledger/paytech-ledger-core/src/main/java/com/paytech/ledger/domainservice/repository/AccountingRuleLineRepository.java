package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;

/** t_accounting_rule_line：规则行随版本一次写入，之后只读。 */
public interface AccountingRuleLineRepository {

    /** 版本全部规则行，按指令类型、行号升序。 */
    List<AccountingRuleLine> findByVersion(long bundleVerId);

    /** 是否有规则集当前生效版本的规则行引用该科目代码。 */
    boolean existsCurrentByAccountCode(String accountCode);

    /** 批量写入一个版本的规则行。 */
    void insertAll(List<AccountingRuleLine> lines);
}
