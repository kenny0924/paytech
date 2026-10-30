package com.paytech.tax.domain.policy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.enums.LiableRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 地区包 treatments 中的一条处理规则：EQ / IN 合取条件与处理结果
 * <li>条件字段只能是 {@link TaxPolicyRules#CONDITION_FIELDS}；缺失字段表示任意值。</li>
 */
@Getter
@AllArgsConstructor
public class TaxTreatmentRule {
    /** 规则代码，包内唯一 */
    private final String ruleCode;

    /** 条件：字段 → 允许取值（EQ 为单元素集合） */
    private final Map<String, Set<String>> when;

    /** 组件代码，例如 VAT */
    private final String componentCode;

    /** 处理类型 */
    private final TaxTreatment treatment;

    /** 法定承担角色 */
    private final LiableRole liableRole;

    /** 原因码 */
    private final String reasonCode;

    /** 税率小数；无适用税率为空 */
    private final BigDecimal rate;

    /**
     * 给定事实是否满足全部条件
     *
     * @param facts 条件字段 → 本行取值
     * @return 每个声明的条件字段都包含本行取值时为 true
     */
    public boolean matches(Map<String, String> facts) {
        for (Map.Entry<String, Set<String>> condition : when.entrySet()) {
            if (!condition.getValue().contains(facts.get(condition.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 与另一条规则是否可能同时命中
     * <li>每个字段两边取值集合都有交集（一边缺失视为任意值）时重叠；互斥校验据此拒绝重叠的规则包。</li>
     *
     * @param other 另一条规则
     * @return 存在同时满足两条规则的事实组合时为 true
     */
    public boolean overlaps(TaxTreatmentRule other) {
        for (String field : TaxPolicyRules.CONDITION_FIELDS) {
            Set<String> mine = when.get(field);
            Set<String> theirs = other.when.get(field);
            if (mine != null && theirs != null && mine.stream().noneMatch(theirs::contains)) {
                return false;
            }
        }
        return true;
    }
}
