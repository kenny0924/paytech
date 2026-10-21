package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.SettlementDirection;

import lombok.Getter;
import lombok.Setter;

/**
 * t_expected_bank_movement（预期银行流水）
 * <li>预期银行流水：银行层要核对的目标（渠道账单净额、出款执行、缴税），由银行匹配逐笔确认。</li>
 */
@Getter
@Setter
public class ExpectedBankMovement {
    /**
     * 预期银行流水ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 预期来源
     * <li>预期来源；切片 1 只产生 CHANNEL_SETTLEMENT。允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX_PAYMENT。</li>
     */
    private ExpectedMovementType movementType;

    /**
     * 来源ID
     * <li>来源 ID：渠道账单 ID / 出款执行 ID / 缴税 ID。</li>
     */
    private Long sourceId;

    /**
     * 银行账户ID
     * <li>预期进出的银行账户，生成时冻结。</li>
     */
    private Long cashAccountId;

    /**
     * 经营主体编号
     * <li>所属经营主体。</li>
     */
    private String entityNo;

    /**
     * 预期方向
     * <li>预期方向。允许值：CREDIT、DEBIT。</li>
     */
    private SettlementDirection direction;

    /**
     * 币种
     * <li>币种。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 预期金额
     * <li>预期金额，正数。</li>
     */
    private BigDecimal expectedAmount;

    /**
     * 预期日期
     * <li>预期到账或扣款日；渠道取结算日。</li>
     */
    private LocalDate expectedDate;

    /**
     * 银行付款参考号
     * <li>出款、缴税的银行付款参考号；渠道为空。</li>
     */
    private String bankPaymentRef;

    /**
     * 已确认金额
     * <li>已确认银行匹配的金额合计。</li>
     */
    private BigDecimal confirmedAmount;

    /**
     * 归类金额
     * <li>经归类处置结清的差额合计（带符号：不足为正，超出为负）。</li>
     */
    private BigDecimal classifiedAmount;

    /**
     * 状态
     * <li>确认进度。允许值：OPEN、PARTIALLY_CONFIRMED、CONFIRMED、CANCELLED、EXCEPTION。</li>
     */
    private ExpectedMovementStatus status;

    /**
     * 乐观锁版本
     * <li>进度更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>生成的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
