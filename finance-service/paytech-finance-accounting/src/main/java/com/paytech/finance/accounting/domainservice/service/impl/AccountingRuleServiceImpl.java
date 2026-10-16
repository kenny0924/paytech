package com.paytech.finance.accounting.domainservice.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.finance.accounting.core.enums.RuleVersionStatus;
import com.paytech.finance.accounting.domain.AccountingRuleVersionContext;
import com.paytech.finance.accounting.domain.view.RuleBundleView;
import com.paytech.finance.accounting.domain.view.RuleVersionView;
import com.paytech.finance.accounting.domainservice.repository.AccountingRuleBundleRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingRuleLineRepository;
import com.paytech.finance.accounting.domainservice.service.AccountingRuleService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

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

    /** 锁规则集与版本后写入批准结果；已到生效时间时替换当前版本并切换指针。 */
    @Override
    public void approveVersion(AccountingRuleVersionContext context, long checkerId, Instant approvedTime) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            AccountingRuleBundleVer ver = context.getVer();
            if (!context.effectiveAt(approvedTime)) {
                accountingRuleBundleVerRepository.review(ver, RuleVersionStatus.APPROVED, checkerId, approvedTime);
                return;
            }
            accountingRuleBundleVerRepository.review(ver, RuleVersionStatus.ACTIVE, checkerId, approvedTime);
            if (context.getCurrentVer() != null) {
                accountingRuleBundleVerRepository.retire(context.getCurrentVer(), ver.getEffectiveFrom());
            }
            accountingRuleBundleRepository.switchCurrentVersion(context.getBundle(), ver.getId());
        });
    }

    /** 锁规则集与版本后写入驳回结果。 */
    @Override
    public void rejectVersion(AccountingRuleVersionContext context, long checkerId) {
        transactionTemplate.executeWithoutResult(status -> {
            lockBundleAndVersion(context);
            accountingRuleBundleVerRepository.review(context.getVer(), RuleVersionStatus.REJECTED, checkerId, null);
        });
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
