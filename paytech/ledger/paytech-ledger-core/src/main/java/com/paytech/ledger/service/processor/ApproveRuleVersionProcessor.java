package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.domainservice.validator.rule.RuleVersionReviewValidator;
import com.paytech.ledger.service.processor.request.ReviewRuleVersionRequest;

import lombok.AllArgsConstructor;

/**
 * 批准规则版本：Checker ≠ Maker，只能批准 PENDING_APPROVAL 版本
 * <li>批准时刻已到生效时间：版本 ACTIVE，原当前版本 RETIRED 且失效时间等于新版本生效时间，规则集当前指针原子切换；
 * 新版本生效时间必须晚于原当前版本，避免生效区间倒置。</li>
 * <li>未到生效时间：版本 APPROVED，当前指针不变；到期由 {@link ActivateRuleVersionProcessor} 切换。</li>
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内先锁规则集再锁版本，指针切换以规则集版本 CAS 保护。</li>
 */
@Component
@AllArgsConstructor
public class ApproveRuleVersionProcessor extends AbstractLockProcessor<ReviewRuleVersionRequest, RuleVersionView> {
    private final Clock clock;
    private final AccountingRuleService accountingRuleService;

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
        AccountingRuleBundleVer currentVer = loaded.effectiveAt(now) ? accountingRuleService.findReplacedVersion(loaded) : null;
        AccountingRuleVersionContext context =
            new AccountingRuleVersionContext(loaded.getBundle(), loaded.getVer(), loaded.getLines(), currentVer);
        accountingRuleService.approveVersion(context, request.getCheckerId(), now);
        return context.toView();
    }
}
