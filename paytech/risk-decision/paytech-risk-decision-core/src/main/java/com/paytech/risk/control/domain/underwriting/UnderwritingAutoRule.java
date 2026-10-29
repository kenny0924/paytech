package com.paytech.risk.control.domain.underwriting;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;

import lombok.Getter;

/**
 * 自动承保判定（U9 与 U11 同一实现）：CRR = LOW、预计月流水不超过该币种阈值、MCC 非高风险（卡组织 ∪ 规则排除清单）、分类已确认
 * <li>没有 ACTIVE 规则、没有该币种阈值、未申报流水或 MCC 未确认都视为不满足。</li>
 */
@Getter
public final class UnderwritingAutoRule {
    /** 判定使用的规则；没有 ACTIVE 规则为空。 */
    private final UnderwritingRule rule;

    /** 全部检查，顺序同 {@link UnderwritingCheckCode} 声明。 */
    private final List<UnderwritingCheck> checks;

    /** 输入币种在规则中的月流水上限（含）；没有规则、未申报流水或该币种无阈值时为空。 */
    private final BigDecimal thresholdAmount;

    /** 上限的币种，等于输入的流水币种；上限为空时为空。 */
    private final String thresholdCurrency;

    /** 由判定结果构造。 */
    private UnderwritingAutoRule(UnderwritingRule rule, List<UnderwritingCheck> checks, BigDecimal thresholdAmount,
        String thresholdCurrency) {
        this.rule = rule;
        this.checks = checks;
        this.thresholdAmount = thresholdAmount;
        this.thresholdCurrency = thresholdCurrency;
    }

    /**
     * 判定输入是否满足自动承保
     *
     * @param input 输入快照
     * @param rule ACTIVE 规则；为空表示未配置
     * @return 判定结果
     */
    public static UnderwritingAutoRule evaluate(UnderwritingInput input, UnderwritingRule rule) {
        boolean volumeProvided = input.getExpectedMonthlyVolume() != null && input.getVolumeCurrency() != null;
        BigDecimal limit = rule == null || !volumeProvided ? null : rule.getThresholds().get(input.getVolumeCurrency());
        boolean mccKnown = input.getMccCode() != null;
        return new UnderwritingAutoRule(rule, List.of(
            new UnderwritingCheck(UnderwritingCheckCode.RULE_CONFIGURED, rule != null),
            new UnderwritingCheck(UnderwritingCheckCode.CRR_LOW, input.getCrr() == UnderwritingRiskLevel.LOW),
            new UnderwritingCheck(UnderwritingCheckCode.VOLUME_PROVIDED, volumeProvided),
            new UnderwritingCheck(UnderwritingCheckCode.CURRENCY_CONFIGURED, limit != null),
            new UnderwritingCheck(UnderwritingCheckCode.VOLUME_WITHIN_THRESHOLD,
                limit != null && input.getExpectedMonthlyVolume().compareTo(limit) <= 0),
            new UnderwritingCheck(UnderwritingCheckCode.MCC_NOT_NETWORK_HIGH_RISK, mccKnown && !input.isNetworkHighRiskMcc()),
            new UnderwritingCheck(UnderwritingCheckCode.MCC_NOT_EXCLUDED,
                mccKnown && rule != null && !rule.getExcludedMccCodes().contains(input.getMccCode())),
            new UnderwritingCheck(UnderwritingCheckCode.CLASSIFICATION_CONFIRMED,
                input.getSourceClassificationConfirmedTime() != null)), limit, limit == null ? null : input.getVolumeCurrency());
    }

    /** 全部检查通过。 */
    public boolean eligible() {
        return checks.stream().allMatch(UnderwritingCheck::isPassed);
    }

    /** 未通过的检查编码，英文逗号分隔，用于错误详情。 */
    public String failedCodes() {
        return checks.stream().filter(check -> !check.isPassed()).map(check -> check.getCode().getCode())
            .collect(Collectors.joining(","));
    }
}
