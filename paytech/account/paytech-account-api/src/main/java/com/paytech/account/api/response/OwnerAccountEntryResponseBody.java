package com.paytech.account.api.response;

import java.time.Instant;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/** Owner 账户的一条明细流水；ID、版本、序号与行号为十进制字符串。 */
@Getter
@Setter
public class OwnerAccountEntryResponseBody {
    /** 流水主键。 */
    private String entryId;

    /** 所属过账批次主键。 */
    private String postingId;

    /** 批次在其过账流内的序号。 */
    private String postingSequence;

    /** 批次来源：ECONOMIC_FACT 或 BALANCE_MOVEMENT。 */
    private String sourceType;

    /** 事实批次：计费事实主键；桶移动批次为空。 */
    private String factId;

    /** 事实批次：计费事实版本；桶移动批次为空。 */
    private String factVersion;

    /** 事实批次：计费事实类型代码（billing {@code BillingFactType}）；桶移动批次为空。 */
    private String factType;

    /** 桶移动批次：移动来源类型；事实批次为空。 */
    private String movementSourceType;

    /** 批次内行号。 */
    private String lineNo;

    /** 来源事实明细行号；桶移动批次为空。 */
    private String factLineNo;

    /** 借贷方向：DEBIT 或 CREDIT。 */
    private String entrySide;

    /** 正金额。 */
    private Money amount;

    /** 被冲回的原流水主键；非冲回行为空。 */
    private String reversesEntryId;

    /** 批次生成的 UTC 时间。 */
    private Instant postedTime;
}
