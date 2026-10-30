package com.paytech.tax.domain.policy;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已通过 Schema 与互斥校验的地区包规则（policy_json 版本 1 的内存形态）
 * <li>只由 {@link TaxPolicyRulesParser#parse(String)} 构造；不可变。</li>
 */
@Getter
@AllArgsConstructor
public class TaxPolicyRules {
    /** 条件语法允许的字段（EQ / IN 合取）。 */
    public static final List<String> CONDITION_FIELDS =
        List.of("supplyKind", "categoryCode", "placeOfSupply", "buyerTaxpayerStatus");

    /** 缺省报价有效小时数。 */
    public static final int DEFAULT_QUOTE_VALIDITY_HOURS = 24;

    /** 适用的供应类型 */
    private final Set<String> supplyKinds;

    /** NOT_COLLECTED 组件的原因码；可空 */
    private final String notCollectedReasonCode;

    /** 供应地算法：本期只有 BUYER_COUNTRY */
    private final String placeOfSupplyAlgorithm;

    /** 税点依据：本期只有 PAYMENT */
    private final String taxPointBasis;

    /** 报价有效小时数 */
    private final int quoteValidityHours;

    /** 处理规则，两两互斥 */
    private final List<TaxTreatmentRule> treatments;

    /** 取整方法 */
    private final RoundingMode roundingMode;

    /** 取整位数；为空时取币种 minor unit */
    private final Integer roundingScale;

    /** 简化税票规则；可空（不开票） */
    private final TaxReceiptRule receipt;

    /** 申报规则代码 */
    private final String reportingRuleCode;

    /** 申报截止规则：PERIOD_END_PLUS_DAYS 或 END_OF_NEXT_MONTH */
    private final String dueDateRule;

    /** PERIOD_END_PLUS_DAYS 的天数；其他规则为空 */
    private final Integer dueDays;

    /**
     * 按事实匹配处理规则
     *
     * @param facts 条件字段 → 本行取值
     * @return 命中的全部规则（调用方要求恰好一条）
     */
    public List<TaxTreatmentRule> match(Map<String, String> facts) {
        return treatments.stream().filter(rule -> rule.matches(facts)).toList();
    }
}
