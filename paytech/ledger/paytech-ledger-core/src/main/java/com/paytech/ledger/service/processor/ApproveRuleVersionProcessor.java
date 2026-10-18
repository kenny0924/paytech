package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.domainservice.validator.rule.RuleVersionReviewValidator;
import com.paytech.ledger.service.processor.request.ReviewRuleVersionRequest;

import lombok.AllArgsConstructor;

/**
 * 批准规则版本：Checker ≠ Maker，只能批准 PENDING_APPROVAL 版本
 * <li>批准时刻已到生效时间：版本 ACTIVE，原当前版本 RETIRED 且失效时间等于新版本生效时间，规则集当前指针原子切换；
 * 新版本生效时间必须晚于原当前版本，避免生效区间倒置。</li>
 * <li>未到生效时间：版本 APPROVED，当前指针不变（到期切换不在本期）。</li>
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内先锁规则集再锁版本，指针切换以规则集版本 CAS 保护。</li>
 */
@Component
@AllArgsConstructor
public class ApproveRuleVersionProcessor extends AbstractLockProcessor<ReviewRuleVersionRequest, RuleVersionView> {
    private final Clock clock;
    private final AccountingRuleService accountingRuleService;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;

    /** 规则集资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ReviewRuleVersionRequest request) {
        return LockCommand.lock(AccountingLocks.accountingRuleBundle(request.getBundleCode()));
    }

    @Override
    protected RuleVersionView processWithinLock(ReviewRuleVersionRequest request) {
        AccountingRuleVersionContext loaded =
            accountingRuleService.loadVersion(request.getBundleCode(), request.getBundleVer());
        RuleVersionReviewValidator.checkReviewable(request.getBundleCode(), loaded.getVer(), request.getCheckerId());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AccountingRuleBundleVer currentVer = loaded.effectiveAt(now) ? getReplacedVersion(loaded) : null;
        AccountingRuleVersionContext context =
            new AccountingRuleVersionContext(loaded.getBundle(), loaded.getVer(), loaded.getLines(), currentVer);
        accountingRuleService.approveVersion(context, request.getCheckerId(), now);
        return context.toView();
    }

    /** 读取将被替代的当前版本；规则集尚无当前版本时为空，新版本生效时间必须晚于它。 */
    AccountingRuleBundleVer getReplacedVersion(AccountingRuleVersionContext context) {
        AccountingRuleBundle bundle = context.getBundle();
        if (bundle.getCurrentVerId() == null) {
            return null;
        }
        AccountingRuleBundleVer currentVer = accountingRuleBundleVerRepository.findById(bundle.getCurrentVerId());
        if (currentVer == null) {
            throw new ErrorException("规则集当前版本指针无对应版本: " + bundle.getBundleCode() + ", " + bundle.getCurrentVerId());
        }
        if (!context.getVer().getEffectiveFrom().isAfter(currentVer.getEffectiveFrom())) {
            throw new BizCheckFailException("新版本生效时间必须晚于当前版本: 规则集 " + bundle.getBundleCode() + ", 版本 "
                + context.getVer().getBundleVer() + ", 当前版本 " + currentVer.getBundleVer());
        }
        return currentVer;
    }
}
