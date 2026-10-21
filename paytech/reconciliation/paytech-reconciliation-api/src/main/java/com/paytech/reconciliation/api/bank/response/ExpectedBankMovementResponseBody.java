package com.paytech.reconciliation.api.bank.response;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 预期银行流水与确认进度。 */
@Getter
@Setter
public class ExpectedBankMovementResponseBody {
    /** 预期银行流水主键 */
    private String expectedMovementId;

    /** 来源：CHANNEL_SETTLEMENT、PAYOUT、TAX_PAYMENT */
    private String movementType;

    /** 来源 ID：渠道账单主键 / 出款执行 ID / 缴税 ID */
    private String sourceId;

    /** 银行账户主键 */
    private String cashAccountId;

    /** 经营主体编号 */
    private String entityNo;

    /** 预期方向：CREDIT、DEBIT */
    private String direction;

    /** 币种 */
    private String currency;

    /** 预期金额，按币种精度补齐 */
    private String expectedAmount;

    /** 预期到账或扣款日 */
    private LocalDate expectedDate;

    /** 出款、缴税的银行付款参考号；渠道为空 */
    private String bankPaymentRef;

    /** 已确认银行匹配的金额合计，按币种精度补齐 */
    private String confirmedAmount;

    /** 经归类结清的差额合计（带符号：不足为正，超出为负），按币种精度补齐 */
    private String classifiedAmount;

    /** 状态：OPEN、PARTIALLY_CONFIRMED、CONFIRMED、CANCELLED、EXCEPTION */
    private String status;

    /** 乐观锁版本 */
    private String version;

    /** 生成的 UTC 时间 */
    private Instant createTime;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;
}
