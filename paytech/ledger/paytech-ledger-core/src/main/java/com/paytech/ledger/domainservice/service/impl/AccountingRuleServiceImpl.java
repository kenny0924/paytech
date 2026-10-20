package com.paytech.ledger.domainservice.service.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleBundleView;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleLineRepository;
import com.paytech.ledger.domainservice.service.AccountingRuleService;

import lombok.AllArgsConstructor;

/** 规则版本的本地保存与查询；锁顺序固定为规则集 → 版本。 */
@Service
@AllArgsConstructor
public class AccountingRuleServiceImpl implements AccountingRuleService {
    private final TransactionTemplate transactionTemplate;
    private final AccountingRuleLineRepository accountingRuleLineRepository;
    private final AccountingRuleBundleRepository accountingRuleBundleRepository;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;

    /** 读取规则集；不存在时业务拒绝。 */
    @Override
    public AccountingRuleBundle loadBundle(String bundleCode) {
        AccountingRuleBundle bundle = accountingRuleBundleRepository.findByCode(bundleCode);
        if (bundle == null) {
            throw new BizCheckFailException("自动记账规则集不存在: " + bundleCode);
        }
        return bundle;
    }

    /** 读取规则集、版本及其规则行；规则集或版本不存在时业务拒绝。 */
    @Override
    public AccountingRuleVersionContext loadVersion(String bundleCode, long bundleVer) {
        AccountingRuleBundle bundle = loadBundle(bundleCode);
        AccountingRuleBundleVer ver = accountingRuleBundleVerRepository.findByBundleVer(bundle.getId(), bundleVer);
        if (ver == null) {
            throw new BizCheckFailException("自动记账规则版本不存在: 规则集 " + bundleCode + ", 版本 " + bundleVer);
        }
        return new AccountingRuleVersionContext(bundle, ver, accountingRuleLineRepository.findByVersion(ver.getId()));
    }

    /** 锁规则集后复验无待处理版本，再写入版本与规则行。 */
    @Override
    public void createVersion(AccountingRuleVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundle(context);
            if (accountingRuleBundleVerRepository.existsOpenVersion(context.getBundle().getId())) {
                throw new ConcurrentRequestException("规则集已有待处理版本: " + context.getBundle().getBundleCode());
            }
            accountingRuleBundleVerRepository.insert(context.getVer());
            accountingRuleLineRepository.insertAll(context.getLines());
        });
    }

    /** 锁规则集与版本后提交待复核。 */
    @Override
    public void submitVersion(AccountingRuleVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            accountingRuleBundleVerRepository.updateStatus(context.getVer(), RuleVersionStatus.PENDING_APPROVAL);
        });
    }

    /**
     * 锁规则集与版本后写入批准结果
     * <li>未到生效时间：APPROVED，生效时间不变。</li>
     * <li>已到生效时间：生效时间改为批准时间并置 ACTIVE（同一条件更新），再替换当前版本、切换指针，原当前版本失效时间因此等于批准时间。</li>
     */
    @Override
    public void approveVersion(AccountingRuleVersionContext context, long checkerId, Instant approvedTime) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            AccountingRuleBundleVer ver = context.getVer();
            if (!context.effectiveAt(approvedTime)) {
                accountingRuleBundleVerRepository.review(ver, RuleVersionStatus.APPROVED, checkerId, approvedTime,
                    ver.getEffectiveFrom());
                return;
            }
            accountingRuleBundleVerRepository.review(ver, RuleVersionStatus.ACTIVE, checkerId, approvedTime,
                approvedTime);
            switchCurrentVersion(context);
        });
    }

    /** 锁规则集与版本后写入驳回结果。 */
    @Override
    public void rejectVersion(AccountingRuleVersionContext context, long checkerId) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            accountingRuleBundleVerRepository.review(context.getVer(), RuleVersionStatus.REJECTED, checkerId, null,
                context.getVer().getEffectiveFrom());
        });
    }

    /** 读取将被替代的当前版本；规则集尚无当前版本时为空，目标版本生效时间必须晚于它。 */
    @Override
    public AccountingRuleBundleVer findReplacedVersion(AccountingRuleVersionContext context) {
        AccountingRuleBundle bundle = context.getBundle();
        AccountingRuleBundleVer currentVer = findCurrentVersion(bundle);
        if (currentVer == null) {
            return null;
        }
        if (!context.getVer().getEffectiveFrom().isAfter(currentVer.getEffectiveFrom())) {
            throw new FailException(LedgerErrorCode.RULE_EFFECTIVE_FROM_INVALID);
        }
        return currentVer;
    }

    /** 在规则集全部版本中按业务时间选择适用版本；没有候选时为空。 */
    @Override
    public AccountingRuleBundleVer findApplicableVersion(AccountingRuleBundle bundle, Instant at) {
        List<AccountingRuleBundleVer> versions = accountingRuleBundleVerRepository.findByBundle(bundle.getId());
        AccountingRuleBundleVer active = versions.stream().filter(ver -> ver.getStatus() == RuleVersionStatus.ACTIVE)
            .findFirst().orElse(null);
        return versions.stream().filter(ver -> intervalCovers(ver, at) || dueSuccessor(ver, active, at))
            .max(Comparator.comparing(AccountingRuleBundleVer::getEffectiveFrom)
                .thenComparing(AccountingRuleBundleVer::getBundleVer))
            .orElse(null);
    }

    /** ACTIVE 或 RETIRED 版本的生效区间 [effective_from, effective_to) 覆盖该时点；RETIRED 缺失效时间视为数据损坏，不作候选。 */
    static boolean intervalCovers(AccountingRuleBundleVer ver, Instant at) {
        if (ver.getStatus() != RuleVersionStatus.ACTIVE && ver.getStatus() != RuleVersionStatus.RETIRED) {
            return false;
        }
        if (ver.getStatus() == RuleVersionStatus.RETIRED && ver.getEffectiveTo() == null) {
            return false;
        }
        return !ver.getEffectiveFrom().isAfter(at) && (ver.getEffectiveTo() == null || at.isBefore(ver.getEffectiveTo()));
    }

    /** 已到生效时间、待定时任务切换的 APPROVED 版本：生效时间须晚于当前 ACTIVE 版本，否则已被取代。 */
    static boolean dueSuccessor(AccountingRuleBundleVer ver, AccountingRuleBundleVer active, Instant at) {
        if (ver.getStatus() != RuleVersionStatus.APPROVED || ver.getEffectiveFrom().isAfter(at)) {
            return false;
        }
        return active == null || ver.getEffectiveFrom().isAfter(active.getEffectiveFrom());
    }

    /** 读取规则集当前版本；尚无当前版本时为空。 */
    @Override
    public AccountingRuleBundleVer findCurrentVersion(AccountingRuleBundle bundle) {
        if (bundle.getCurrentVerId() == null) {
            return null;
        }
        AccountingRuleBundleVer currentVer = accountingRuleBundleVerRepository.findById(bundle.getCurrentVerId());
        if (currentVer == null) {
            throw new ErrorException("规则集当前版本指针无对应版本: " + bundle.getBundleCode() + ", " + bundle.getCurrentVerId());
        }
        return currentVer;
    }

    /** 锁规则集与版本后把被取代的 APPROVED 版本置为 RETIRED，失效时间等于自身生效时间。 */
    @Override
    public void retireSupersededVersion(AccountingRuleVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            accountingRuleBundleVerRepository.retireSuperseded(context.getVer());
        });
    }

    /** 锁规则集与版本后把 APPROVED 版本切换为当前版本。 */
    @Override
    public void activateVersion(AccountingRuleVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            accountingRuleBundleVerRepository.activate(context.getVer());
            switchCurrentVersion(context);
        });
    }

    /** 锁规则集与版本后作废草稿。 */
    @Override
    public void voidVersion(AccountingRuleVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            accountingRuleBundleVerRepository.updateStatus(context.getVer(), RuleVersionStatus.VOIDED);
        });
    }

    /** 全部规则集及其版本摘要。 */
    @Override
    public List<RuleBundleView> listBundles() {
        return accountingRuleBundleRepository.findAll().stream()
            .map(bundle -> RuleBundleView.of(bundle, accountingRuleBundleVerRepository.findByBundle(bundle.getId())))
            .toList();
    }

    /** 查询规则集与全部版本摘要。 */
    @Override
    public RuleBundleView getBundle(String bundleCode) {
        AccountingRuleBundle bundle = loadBundle(bundleCode);
        return RuleBundleView.of(bundle, accountingRuleBundleVerRepository.findByBundle(bundle.getId()));
    }

    /** 查询版本详情。 */
    @Override
    public RuleVersionView getVersion(String bundleCode, long bundleVer) {
        return loadVersion(bundleCode, bundleVer).toView();
    }

    /** 参与调用方事务：原当前版本（如有）RETIRED 且失效时间为目标版本生效时间，规则集当前指针切换到目标版本。 */
    void switchCurrentVersion(AccountingRuleVersionContext context) {
        AccountingRuleBundleVer ver = context.getVer();
        if (context.getCurrentVer() != null) {
            accountingRuleBundleVerRepository.retire(context.getCurrentVer(), ver.getEffectiveFrom());
        }
        accountingRuleBundleRepository.switchCurrentVersion(context.getBundle(), ver.getId());
    }

    /** 事务内对规则集行加排他锁。 */
    void lockBundle(AccountingRuleVersionContext context) {
        if (accountingRuleBundleRepository.lockByCode(context.getBundle().getBundleCode()) == null) {
            throw new ErrorException("自动记账规则集在写入时不存在: " + context.getBundle().getBundleCode());
        }
    }

    /** 事务内按规则集 → 版本顺序加排他锁。 */
    void lockBundleAndVersion(AccountingRuleVersionContext context) {
        lockBundle(context);
        AccountingRuleBundleVer ver = context.getVer();
        if (accountingRuleBundleVerRepository.lockByBundleVer(ver.getBundleId(), ver.getBundleVer()) == null) {
            throw new ErrorException("自动记账规则版本在写入时不存在: " + ver.getId());
        }
    }
}
