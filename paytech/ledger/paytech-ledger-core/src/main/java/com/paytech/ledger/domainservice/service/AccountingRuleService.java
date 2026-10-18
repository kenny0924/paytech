package com.paytech.ledger.domainservice.service;

import java.time.Instant;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleBundleView;
import com.paytech.ledger.domain.view.RuleVersionView;

/**
 * 自动记账规则版本的本地保存、状态迁移与查询
 * <li>写入事务统一先锁规则集行、再锁目标版本行；状态迁移以原状态为条件，当前版本切换以规则集版本为条件。</li>
 * <li>只操作本模块数据库，不调用外部依赖。</li>
 */
public interface AccountingRuleService {

    /** 读取规则集；不存在时业务拒绝。 */
    AccountingRuleBundle loadBundle(String bundleCode);

    /** 读取规则集、版本及其规则行；规则集或版本不存在时业务拒绝。 */
    AccountingRuleVersionContext loadVersion(String bundleCode, long bundleVer);

    /**
     * 一个事务内锁规则集、复验无 DRAFT/PENDING_APPROVAL 版本、写入 DRAFT 版本与全部规则行
     * <li>已有待处理版本或版本号重复时按并发冲突拒绝。</li>
     */
    void createVersion(AccountingRuleVersionContext context);

    /** 一个事务内锁规则集与版本，DRAFT → PENDING_APPROVAL。 */
    void submitVersion(AccountingRuleVersionContext context);

    /**
     * 一个事务内锁规则集与版本并写入批准结果
     * <li>已到生效时间：版本 ACTIVE、原当前版本 RETIRED 且失效时间为新版本生效时间、规则集当前指针切换。</li>
     * <li>未到生效时间：版本 APPROVED，当前指针不变。</li>
     */
    void approveVersion(AccountingRuleVersionContext context, long checkerId, Instant approvedTime);

    /** 一个事务内锁规则集与版本，PENDING_APPROVAL → REJECTED 并写入复核人。 */
    void rejectVersion(AccountingRuleVersionContext context, long checkerId);

    /** 查询规则集与全部版本摘要；不存在时业务拒绝。 */
    RuleBundleView getBundle(String bundleCode);

    /** 查询版本详情；规则集或版本不存在时业务拒绝。 */
    RuleVersionView getVersion(String bundleCode, long bundleVer);
}
