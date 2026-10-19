package com.paytech.account.domain.view;

import java.util.List;

import com.paytech.account.api.enums.FactApplyStatus;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.core.enums.AppliedFactStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一个入账批次及其来源与全部流水（批次、事实、移动、流水、账户五表组合）：AccountPosted 载荷、追平列表与批次详情共用
 * <li>事实批次 appliedFact 非空、movement 为空；桶移动批次相反。</li>
 */
@Getter
@AllArgsConstructor
public class AccountPostingView {
    /** 批次。 */
    private final AccountPosting posting;

    /** 事实批次的处理记录；桶移动批次为空。 */
    private final BalanceAppliedFact appliedFact;

    /** 桶移动批次的移动记录；事实批次为空。 */
    private final BalanceMovement movement;

    /** 按行号升序的流水。 */
    private final List<Line> lines;

    /**
     * 事实批次在入账当时的应用结果
     * <li>SKIPPED 是终态；APPLIED 之后可能被替代为 REVERSED，但入账当时为 APPLIED。</li>
     *
     * @return 事实批次的应用结果；桶移动批次为空
     */
    public FactApplyStatus factApplyStatus() {
        if (appliedFact == null) {
            return null;
        }
        return appliedFact.getApplyStatus() == AppliedFactStatus.SKIPPED ? FactApplyStatus.SKIPPED
            : FactApplyStatus.APPLIED;
    }

    /** 一行流水与其 Owner 账户。 */
    @Getter
    @AllArgsConstructor
    public static class Line {
        /** 流水。 */
        private final AccountEntry entry;

        /** 流水所在的 Owner 账户。 */
        private final OwnerAccount account;
    }
}
