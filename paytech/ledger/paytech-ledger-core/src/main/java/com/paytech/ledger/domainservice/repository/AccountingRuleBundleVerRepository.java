package com.paytech.ledger.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;

/** t_accounting_rule_bundle_ver：规则版本读取、创建与状态迁移。 */
public interface AccountingRuleBundleVerRepository {

    /** 按主键读取，不存在返回空。 */
    AccountingRuleBundleVer findById(long id);

    /** 按规则集与版本号读取，不存在返回空。 */
    AccountingRuleBundleVer findByBundleVer(long bundleId, long bundleVer);

    /** 按规则集与版本号读取并加排他锁，不存在返回空；必须在调用方事务内。 */
    AccountingRuleBundleVer lockByBundleVer(long bundleId, long bundleVer);

    /** 规则集全部版本，按版本号降序。 */
    List<AccountingRuleBundleVer> findByBundle(long bundleId);

    /** 已到生效时间的 APPROVED 版本（跨规则集），按生效时间、规则集、版本号升序。 */
    List<AccountingRuleBundleVer> findDueApproved(Instant asOf);

    /** 规则集的下一个版本号（当前最大值 + 1）。 */
    long nextBundleVer(long bundleId);

    /** 规则集是否已有 DRAFT 或 PENDING_APPROVAL 版本。 */
    boolean existsOpenVersion(long bundleId);

    /** 写入新版本；版本号重复时按并发冲突拒绝。 */
    void insert(AccountingRuleBundleVer ver);

    /** 以原状态为条件迁移到新状态，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void updateStatus(AccountingRuleBundleVer ver, RuleVersionStatus status);

    /**
     * 以 PENDING_APPROVAL 为条件写入复核结果与生效时间（同一条件更新），成功后同步原对象；状态已变化时按并发冲突拒绝
     *
     * @param status APPROVED、ACTIVE 或 REJECTED
     * @param approvedTime 批准时间，驳回时为空
     * @param effectiveFrom 复核后的生效时间；审批时已过的生效时间由调用方改为批准时间，其余传入原值
     */
    void review(AccountingRuleBundleVer ver, RuleVersionStatus status, long checkerId, Instant approvedTime,
        Instant effectiveFrom);

    /** 以 APPROVED 为条件把到期版本置为 ACTIVE，复核结果不变，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void activate(AccountingRuleBundleVer ver);

    /** 以 APPROVED 为条件把从未生效、已被取代的版本置为 RETIRED，失效时间等于自身生效时间，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void retireSuperseded(AccountingRuleBundleVer ver);

    /** 以 ACTIVE 为条件把版本置为 RETIRED 并写入失效时间，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void retire(AccountingRuleBundleVer ver, Instant effectiveTo);
}
