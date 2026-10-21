package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PayoutComponentType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_bank_match（银行匹配）
 * <li>银行匹配：银行流水与预期银行流水的多对多分配；确认时锁流水，同一流水已确认的带符号合计不超过流水金额。</li>
 */
@Getter
@Setter
public class BankMatch {
    /**
     * 银行匹配ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 银行流水ID
     * <li>银行流水。</li>
     */
    private Long bankStatementLineId;

    /**
     * 预期银行流水ID
     * <li>预期银行流水，必须与流水同一银行账户。</li>
     */
    private Long expectedMovementId;

    /**
     * 已匹配金额
     * <li>本次分配的金额，正数；对流水的带符号方向由预期流水方向与流水方向是否相同决定。</li>
     */
    private BigDecimal matchedAmount;

    /**
     * 匹配方式
     * <li>AUTO 只表示系统建议，确认均为人工。允许值：AUTO、MANUAL。</li>
     */
    private MatchMethod matchMethod;

    /**
     * 出款组成部分
     * <li>出款流水的组成部分，仅 PAYOUT 使用（切片 3）；其他为空。允许值：BENEFICIARY_TRANSFER、BANK_FEE、COMBINED。</li>
     */
    private PayoutComponentType payoutComponentType;

    /**
     * 状态
     * <li>匹配状态；只有 CONFIRMED 产生下游事件。允许值：PROPOSED、CONFIRMED、REJECTED。</li>
     */
    private BankMatchStatus status;

    /**
     * 处置ID
     * <li>由 RE_MATCH 处置建立时指向该处置；其他为空。</li>
     */
    private Long resolutionId;

    /**
     * 操作人ID
     * <li>确认或拒绝的 Platform 账户 ID；建议未处理时为空。</li>
     */
    private Long operatorId;

    /**
     * 复核时间
     * <li>确认或拒绝的 UTC 时间。</li>
     */
    private Instant decisionTime;

    /**
     * 乐观锁版本
     * <li>状态更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>建立的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
