package com.paytech.ledger.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
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
     * <li>已到生效时间：版本生效时间改为批准时间（审批前已过的时段不被新规则覆盖），版本 ACTIVE、原当前版本 RETIRED 且失效时间为该批准时间、
     * 规则集当前指针切换。</li>
     * <li>未到生效时间：版本 APPROVED，生效时间与当前指针不变。</li>
     */
    void approveVersion(AccountingRuleVersionContext context, long checkerId, Instant approvedTime);

    /** 一个事务内锁规则集与版本，PENDING_APPROVAL → REJECTED 并写入复核人。 */
    void rejectVersion(AccountingRuleVersionContext context, long checkerId);

    /**
     * 读取目标版本生效时将被替代的当前版本
     * <li>规则集尚无当前版本时返回空；当前指针无对应版本为系统错误。</li>
     * <li>目标版本生效时间必须晚于当前版本，避免生效区间倒置，否则业务拒绝。</li>
     */
    AccountingRuleBundleVer findReplacedVersion(AccountingRuleVersionContext context);

    /**
     * 按业务时间选择规则集适用的版本，不加锁、只读已提交数据
     * <li>ACTIVE 或 RETIRED 版本的生效区间 [effective_from, effective_to) 覆盖该时点即为候选；RETIRED 缺失效时间视为数据损坏，不作候选。</li>
     * <li>已到生效时间、尚未被定时任务切换的 APPROVED 版本，在生效时间晚于当前 ACTIVE 版本（或无 ACTIVE 版本）时按切换后的区间参与，
     * 已被取代、等待置为 RETIRED 的版本不参与。</li>
     * <li>取生效时间最晚的候选，相同取版本号较大者；VOIDED、REJECTED、DRAFT、PENDING_APPROVAL 与零长度区间从不选中。</li>
     *
     * @return 适用版本；没有候选（业务时间早于首个版本或数据缺失）时返回空，由调用方按其上下文拒绝
     */
    AccountingRuleBundleVer findApplicableVersion(AccountingRuleBundle bundle, Instant at);

    /** 读取规则集当前版本；尚无当前版本时返回空，当前指针无对应版本为系统错误。 */
    AccountingRuleBundleVer findCurrentVersion(AccountingRuleBundle bundle);

    /**
     * 一个事务内锁规则集与版本，把已被当前版本取代的 APPROVED 版本置为 RETIRED（从未生效）
     * <li>失效时间写为自身生效时间，即零长度生效区间；规则集当前指针不变。</li>
     */
    void retireSupersededVersion(AccountingRuleVersionContext context);

    /**
     * 一个事务内锁规则集与版本，把到期的 APPROVED 版本切换为当前版本
     * <li>版本 ACTIVE、原当前版本 RETIRED 且失效时间为新版本生效时间、规则集当前指针切换；复核结果不变。</li>
     */
    void activateVersion(AccountingRuleVersionContext context);

    /** 一个事务内锁规则集与版本，DRAFT → VOIDED。 */
    void voidVersion(AccountingRuleVersionContext context);

    /** 全部规则集及其版本摘要，按规则集代码升序。 */
    List<RuleBundleView> listBundles();

    /** 查询规则集与全部版本摘要；不存在时业务拒绝。 */
    RuleBundleView getBundle(String bundleCode);

    /** 查询版本详情；规则集或版本不存在时业务拒绝。 */
    RuleVersionView getVersion(String bundleCode, long bundleVer);
}
