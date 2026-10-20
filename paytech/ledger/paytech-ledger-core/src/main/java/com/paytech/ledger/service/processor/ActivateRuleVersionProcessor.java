package com.paytech.ledger.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.service.processor.request.ActivateRuleVersionRequest;

import lombok.AllArgsConstructor;

/**
 * 到期生效：把已到生效时间的 APPROVED 规则版本切换为当前版本（与批准时立即生效相同的切换）
 * <li>原当前版本 RETIRED 且失效时间等于新版本生效时间，规则集当前指针切换。</li>
 * <li>已被取代：生效时间不晚于当前 ACTIVE 版本（如任务停机期间更新的版本已立即生效）时不生效，置为 RETIRED（失效时间等于自身生效时间）并记 WARN，
 * 指针不变，避免每轮失败。</li>
 * <li>幂等：取锁后重读版本，已不是 APPROVED（已生效、已退役或被并发处理）或尚未到期时直接返回当前版本，不写入。</li>
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内先锁规则集再锁版本，状态以原状态条件更新，指针切换以规则集版本 CAS 保护。</li>
 */
@Component
@AllArgsConstructor
public class ActivateRuleVersionProcessor extends AbstractLockProcessor<ActivateRuleVersionRequest, RuleVersionView> {
    private static final Logger LOG = LoggerFactory.getLogger(ActivateRuleVersionProcessor.class);

    private final AccountingRuleService accountingRuleService;

    /** 规则集资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ActivateRuleVersionRequest request) {
        return LockCommand.lock(AccountingLocks.accountingRuleBundle(request.getBundleCode()));
    }

    @Override
    protected RuleVersionView processWithinLock(ActivateRuleVersionRequest request) {
        AccountingRuleVersionContext loaded =
            accountingRuleService.loadVersion(request.getBundleCode(), request.getBundleVer());
        if (loaded.getVer().getStatus() != RuleVersionStatus.APPROVED || !loaded.effectiveAt(request.getAsOf())) {
            return loaded.toView();
        }
        AccountingRuleBundleVer currentVer = accountingRuleService.findCurrentVersion(loaded.getBundle());
        if (currentVer != null && !loaded.getVer().getEffectiveFrom().isAfter(currentVer.getEffectiveFrom())) {
            return retireSuperseded(loaded, currentVer);
        }
        AccountingRuleVersionContext context =
            new AccountingRuleVersionContext(loaded.getBundle(), loaded.getVer(), loaded.getLines(), currentVer);
        accountingRuleService.activateVersion(context);
        return context.toView();
    }

    /** 已被当前版本取代：置为 RETIRED 并记 WARN，不切换指针。 */
    RuleVersionView retireSuperseded(AccountingRuleVersionContext loaded, AccountingRuleBundleVer currentVer) {
        accountingRuleService.retireSupersededVersion(loaded);
        LOG.warn("规则版本已被取代，不再生效并置为 RETIRED: 规则集 {}, 版本 {}, 原因 生效时间 {} 不晚于当前版本 {} 的生效时间 {}",
            loaded.getBundle().getBundleCode(), loaded.getVer().getBundleVer(), loaded.getVer().getEffectiveFrom(),
            currentVer.getBundleVer(), currentVer.getEffectiveFrom());
        return loaded.toView();
    }
}
