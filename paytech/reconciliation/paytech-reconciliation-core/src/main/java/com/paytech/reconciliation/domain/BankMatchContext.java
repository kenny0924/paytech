package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 确认银行匹配的上下文（确认建议、人工多选匹配、银行层 RE_MATCH 处置共用）
 * <li>持有已在锁内读取的银行流水、所属对账单、各条分配的预期流水与渠道账单；{@link #check()} 校验设计 §6.4 不变量 5–6。</li>
 * <li>分配的 match 为空表示新建 CONFIRMED 匹配，否则把该 PROPOSED 建议转为 CONFIRMED。</li>
 */
@Getter
@AllArgsConstructor
public class BankMatchContext {
    /** 银行流水 */
    private final BankStatementLine line;

    /** 银行流水所属对账单 */
    private final BankStatement statement;

    /** 本次分配，顺序同请求 */
    private final List<Allocation> allocations;

    /** CHANNEL_SETTLEMENT 预期流水来源的渠道账单，按预期流水主键 */
    private final Map<Long, ChannelStatement> channelStatements;

    /** 新建匹配的方式 */
    private final MatchMethod matchMethod;

    /** 操作人（确认人） */
    private final Long operatorId;

    /** RE_MATCH 处置主键；其他为空 */
    private final Long resolutionId;

    /**
     * 校验分配：对账单已导入、同一银行账户、预期流水可确认且不重复、金额为正且不超过币种精度与预期剩余、
     * 流水带符号合计落在 0 到流水金额之间
     */
    public void check() {
        String context = "银行流水 " + line.getId();
        if (statement.getStatus() != BankStatementStatus.IMPORTED || line.getMatchStatus() == LineMatchStatus.SUSPENDED) {
            throw new BizCheckFailException("银行流水所属对账单未导入完成或流水已暂记，不能匹配: " + context);
        }
        Set<Long> seen = new HashSet<>();
        for (Allocation allocation : allocations) {
            checkAllocation(allocation, context);
            if (!seen.add(allocation.getMovement().getId())) {
                throw new BizCheckFailException("同一预期银行流水在一次匹配中重复出现: " + context + ", 预期流水 "
                    + allocation.getMovement().getId());
            }
        }
        BigDecimal matched = newLineMatchedAmount();
        if (matched.signum() < 0 || matched.compareTo(line.getAmount()) > 0) {
            throw new BizCheckFailException("匹配后带符号合计超出银行流水金额: " + context + ", 流水金额 "
                + line.getAmount().toPlainString() + ", 匹配后合计 " + matched.toPlainString());
        }
    }

    /** 单条分配的检查。 */
    void checkAllocation(Allocation allocation, String context) {
        ExpectedBankMovement movement = allocation.getMovement();
        String target = context + ", 预期流水 " + movement.getId();
        if (!movement.getCashAccountId().equals(line.getCashAccountId())) {
            throw new BizCheckFailException("预期银行流水与银行流水不是同一银行账户: " + target);
        }
        if (movement.getStatus() == ExpectedMovementStatus.CONFIRMED
            || movement.getStatus() == ExpectedMovementStatus.CANCELLED) {
            throw new BizCheckFailException("预期银行流水已确认或已取消: " + target + ", 状态 " + movement.getStatus().getCode());
        }
        if (allocation.getAmount().signum() <= 0) {
            throw new BizCheckFailException("分配金额必须大于 0: " + target);
        }
        Amounts.checkPrecision(movement.getCurrency(), allocation.getAmount(), target + " 分配金额");
        BigDecimal remaining = BankMatching.movementRemaining(movement);
        if (allocation.getAmount().compareTo(remaining) > 0) {
            throw new BizCheckFailException("分配金额超过预期银行流水剩余金额: " + target + ", 剩余 " + remaining.toPlainString()
                + ", 分配 " + allocation.getAmount().toPlainString());
        }
        ChannelStatement channelStatement = channelStatements.get(movement.getId());
        if (movement.getMovementType() == ExpectedMovementType.CHANNEL_SETTLEMENT && (channelStatement == null
            || channelStatement.getStatus() != ChannelStatementStatus.RECONCILED)) {
            throw new BizCheckFailException("预期银行流水的渠道账单不在已对平待到账状态: " + target);
        }
    }

    /** 本次分配后流水的已匹配带符号合计。 */
    public BigDecimal newLineMatchedAmount() {
        BigDecimal matched = line.getMatchedAmount();
        for (Allocation allocation : allocations) {
            matched = matched.add(BankMatching.signedContribution(line, allocation.getMovement(),
                allocation.getAmount()));
        }
        return matched;
    }

    /** 一条分配：预期流水、金额与（确认建议时）原建议。 */
    @Getter
    @AllArgsConstructor
    public static class Allocation {
        /** 预期银行流水 */
        private final ExpectedBankMovement movement;

        /** 分配金额，正数 */
        private final BigDecimal amount;

        /** 被确认的 PROPOSED 建议；新建匹配为空 */
        private final BankMatch proposal;
    }

    /** 装载上下文前的一条分配输入（预期流水只给主键）。 */
    @Getter
    @AllArgsConstructor
    public static class AllocationInput {
        /** 预期银行流水主键 */
        private final Long expectedMovementId;

        /** 分配金额 */
        private final BigDecimal amount;

        /** 被确认的 PROPOSED 建议；新建匹配为空 */
        private final BankMatch proposal;
    }
}
