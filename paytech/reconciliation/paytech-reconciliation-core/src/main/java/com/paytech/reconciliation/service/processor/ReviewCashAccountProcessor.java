package com.paytech.reconciliation.service.processor;

import java.util.List;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.service.CashAccountService;
import com.paytech.reconciliation.service.processor.request.ReviewCashAccountRequest;
import lombok.AllArgsConstructor;

/**
 * 复核银行账户新建或关闭申请（UC-R02，#9，AC-03）
 * <li>锁 cash-account:{id} 与该账户的主体 + 币种 + 用途范围锁：批准新建时校验同范围只有一个 ACTIVE，并发批准同范围两个账户被串行。</li>
 * <li>复核人必须不同于最近一次提交人，确认的摘要必须等于 contentHash；没有待复核内容时拒绝（重复复核同样拒绝）。</li>
 * <li>批准新建 → ACTIVE；拒绝新建 → CLOSED（决定 I-04）；批准关闭 → CLOSED；拒绝关闭 → 清空申请。</li>
 */
@Component
@AllArgsConstructor
public class ReviewCashAccountProcessor extends AbstractLockProcessor<ReviewCashAccountRequest, CashAccount> {
    private final CashAccountService cashAccountService;
    private final CashAccountRepository cashAccountRepository;

    /** 银行账户锁与范围锁；账户不存在时只锁账户，由处理阶段拒绝。 */
    @Override
    protected LockCommand lockCommand(ReviewCashAccountRequest request) {
        String accountKey = ReconciliationLocks.cashAccount(request.getCashAccountId());
        CashAccount account = cashAccountRepository.findById(request.getCashAccountId());
        List<String> keys = account == null ? List.of(accountKey) : List.of(accountKey,
            ReconciliationLocks.cashAccountScope(account.getEntityType().getCode(), account.getEntityId(),
                account.getCurrency(), account.getAccountPurpose().getCode()));
        return LockCommand.tryLocks(keys, ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected CashAccount processWithinLock(ReviewCashAccountRequest request) {
        // 业务验证
        CashAccount account = loadPending(request);
        checkReviewer(account, request);

        // 本地保存
        if (request.getDecision() == CheckerDecision.REJECTED) {
            cashAccountService.reject(account, request.getActorId());
            return account;
        }
        if (account.getStatus() == CashAccountStatus.PENDING_VERIFICATION) {
            checkSingleActive(account);
        }
        cashAccountService.approve(account, request.getActorId());
        return account;
    }

    /** 账户存在且有待复核内容（新建待复核或关闭申请）。 */
    CashAccount loadPending(ReviewCashAccountRequest request) {
        CashAccount account = cashAccountRepository.findById(request.getCashAccountId());
        if (account == null) {
            throw new BizCheckFailException("银行账户不存在: " + request.getCashAccountId());
        }
        boolean pendingCreate = account.getStatus() == CashAccountStatus.PENDING_VERIFICATION;
        boolean pendingClose = account.getStatus() == CashAccountStatus.ACTIVE
            && account.getPendingAction() == CashAccountPendingAction.CLOSE;
        if (!pendingCreate && !pendingClose) {
            throw new BizCheckFailException("银行账户没有待复核的内容: " + account.getId() + ", 状态 "
                + account.getStatus().getCode());
        }
        return account;
    }

    /** 复核人不同于提交人，确认的摘要与提交一致。 */
    static void checkReviewer(CashAccount account, ReviewCashAccountRequest request) {
        if (account.getMakerId().equals(request.getActorId())) {
            throw new BizCheckFailException("提交人不能复核自己的提交: 银行账户 " + account.getId() + ", 操作人 "
                + request.getActorId());
        }
        if (!account.getContentHash().equals(request.getApprovedContentHash())) {
            throw new BizCheckFailException("确认的内容摘要与提交不一致，请刷新后重新复核: 银行账户 " + account.getId());
        }
    }

    /** 同主体 + 币种 + 用途不能已有 ACTIVE 账户。 */
    void checkSingleActive(CashAccount account) {
        List<CashAccount> active = cashAccountRepository.listByScope(account.getEntityType(), account.getEntityId(),
            account.getCurrency(), account.getAccountPurpose(), CashAccountStatus.ACTIVE);
        if (!active.isEmpty()) {
            throw new BizCheckFailException("同一主体 + 币种 + 用途已有有效银行账户: " + account.getEntityId() + ", "
                + account.getCurrency() + ", " + account.getAccountPurpose().getCode() + ", 已有账户 "
                + active.getFirst().getId());
        }
    }
}
