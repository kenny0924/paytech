package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;

/** t_accounting_rule_bundle：规则集读取与当前版本指针切换。 */
public interface AccountingRuleBundleRepository {

    /** 全部规则集，按代码升序。 */
    List<AccountingRuleBundle> findAll();

    /** 按代码读取规则集，不存在返回空。 */
    AccountingRuleBundle findByCode(String bundleCode);

    /** 按代码读取并加排他锁，不存在返回空；必须在调用方事务内。 */
    AccountingRuleBundle lockByCode(String bundleCode);

    /** 以期望版本条件切换当前版本指针并使版本号加一，成功后同步原对象；版本已变化时按并发冲突拒绝。 */
    void switchCurrentVersion(AccountingRuleBundle bundle, long currentVerId);
}
