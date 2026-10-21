package com.paytech.reconciliation.api.bank.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 银行匹配：一条银行流水对一条预期银行流水的分配。 */
@Getter
@Setter
public class BankMatchResponseBody {
    /** 银行匹配主键 */
    private String bankMatchId;

    /** 银行流水主键 */
    private String bankStatementLineId;

    /** 预期银行流水主键 */
    private String expectedMovementId;

    /**
     * 分配金额
     * <li>正数，按币种精度补齐；对流水的带符号方向由预期流水方向与流水方向是否相同决定。</li>
     */
    private String matchedAmount;

    /** 匹配方式：AUTO（系统建议）、MANUAL */
    private String matchMethod;

    /** 出款组成部分；仅 PAYOUT 使用（切片 3），其他为空 */
    private String payoutComponentType;

    /** 状态：PROPOSED、CONFIRMED、REJECTED */
    private String status;

    /** 由 RE_MATCH 处置建立时的处置主键；其他为空 */
    private String resolutionId;

    /** 确认或拒绝人 Account ID；建议未处理时为空 */
    private String operatorId;

    /** 确认或拒绝的 UTC 时间；建议未处理时为空 */
    private Instant decisionTime;

    /** 建立的 UTC 时间 */
    private Instant createTime;
}
