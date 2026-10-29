package com.paytech.risk.control.domain.underwriting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.paytech.risk.control.core.enums.FulfillmentType;

import lombok.Getter;

/**
 * 估算敞口（B10，参考值，不做门禁、不参与自动承保判定）
 * <li>金额 = 预计月流水 × 风险天数 ÷ 30，HALF_UP 保留 2 位，币种同预计月流水。</li>
 * <li>风险天数：PRE_ORDER = 交付天数 + T+N；INSTANT、SERVICE = T+N；SUBSCRIPTION = 30 + T+N；T+N 为空按 0 并标注。</li>
 */
@Getter
public final class ExposureEstimate {
    /** 一个月按 30 天，也是订阅的一个计费周期。 */
    static final int DAYS_PER_MONTH = 30;

    /** 计算使用的预计月流水；不可算为空。 */
    private final BigDecimal monthlyVolume;

    /** 敞口金额；不可算为空。 */
    private final BigDecimal amount;

    /** 敞口币种；不可算为空。 */
    private final String currency;

    /** 风险天数；不可算为空。 */
    private final Integer riskDays;

    /** 结算周期建议未填、按 0 计算。 */
    private final boolean settlementDaysAssumed;

    /** 不可算原因；可算时为空。 */
    private final ExposureUnavailableReason unavailableReason;

    /** 由计算或已冻结的列值构造。 */
    private ExposureEstimate(BigDecimal monthlyVolume, BigDecimal amount, String currency, Integer riskDays,
        boolean settlementDaysAssumed, ExposureUnavailableReason unavailableReason) {
        this.monthlyVolume = monthlyVolume;
        this.amount = amount;
        this.currency = currency;
        this.riskDays = riskDays;
        this.settlementDaysAssumed = settlementDaysAssumed;
        this.unavailableReason = unavailableReason;
    }

    /**
     * 按输入与结算周期建议计算
     *
     * @param input 输入快照
     * @param settlementDays 结算周期建议 T+N 的 N；为空按 0
     * @return 可算时带金额与风险天数，否则带原因
     */
    public static ExposureEstimate estimate(UnderwritingInput input, Integer settlementDays) {
        boolean assumed = settlementDays == null;
        if (input.getExpectedMonthlyVolume() == null || input.getVolumeCurrency() == null) {
            return unavailable(ExposureUnavailableReason.VOLUME_MISSING, assumed);
        }
        if (input.getFulfillmentType() == null) {
            return unavailable(ExposureUnavailableReason.FULFILLMENT_TYPE_MISSING, assumed);
        }
        if (input.getFulfillmentType() == FulfillmentType.PRE_ORDER && input.getFulfillmentDays() == null) {
            return unavailable(ExposureUnavailableReason.FULFILLMENT_DAYS_MISSING, assumed);
        }
        int riskDays = baseDays(input) + (assumed ? 0 : settlementDays);
        BigDecimal amount = input.getExpectedMonthlyVolume().multiply(BigDecimal.valueOf(riskDays))
            .divide(BigDecimal.valueOf(DAYS_PER_MONTH), 2, RoundingMode.HALF_UP);
        return new ExposureEstimate(input.getExpectedMonthlyVolume(), amount, input.getVolumeCurrency(), riskDays, assumed,
            null);
    }

    /**
     * 从已冻结的版本列还原；金额为空时按输入重新判定不可算原因
     *
     * @param input 版本行的输入快照
     * @param amount 冻结的敞口金额
     * @param riskDays 冻结的风险天数
     * @param settlementDays 版本的结算周期建议
     * @return 冻结的估算敞口
     */
    public static ExposureEstimate frozen(UnderwritingInput input, BigDecimal amount, Integer riskDays,
        Integer settlementDays) {
        if (amount == null || riskDays == null) {
            return estimate(input, settlementDays);
        }
        return new ExposureEstimate(input.getExpectedMonthlyVolume(), amount.setScale(2, RoundingMode.HALF_UP),
            input.getVolumeCurrency(), riskDays, settlementDays == null, null);
    }

    /** 是否可估算。 */
    public boolean available() {
        return unavailableReason == null;
    }

    /** 交付方式对应的基础风险天数（不含 T+N）。 */
    static int baseDays(UnderwritingInput input) {
        return switch (input.getFulfillmentType()) {
            case PRE_ORDER -> input.getFulfillmentDays();
            case SUBSCRIPTION -> DAYS_PER_MONTH;
            case INSTANT, SERVICE -> 0;
        };
    }

    /** 不可算的结果。 */
    static ExposureEstimate unavailable(ExposureUnavailableReason reason, boolean assumed) {
        return new ExposureEstimate(null, null, null, null, assumed, reason);
    }
}
