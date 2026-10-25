package com.paytech.commercial.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;

import lombok.EqualsAndHashCode;

/**
 * 一个合同开通项的接受条件：同维度多值按 OR，异维度按 AND，未写的维度视为全集
 * <li>由 t_agreement_capability_limit 行构造；不可变，纯判断，不访问数据库。</li>
 * <li>价格命中、相关性、完整性、Checkout 解析与基于模板生成共用同一套相容规则。</li>
 * <li>按取值相等：Checkout 复核比较两次读取的候选，同样的条件行得到相等的条件。</li>
 */
@EqualsAndHashCode
public final class CapabilityConditions {
    private final Map<PricingDimension, Set<String>> values;

    /**
     * 直接由维度到取值构造。
     *
     * @param values 维度到取值，空集合的维度忽略
     */
    public CapabilityConditions(Map<PricingDimension, ? extends Collection<String>> values) {
        Map<PricingDimension, Set<String>> copy = new EnumMap<>(PricingDimension.class);
        values.forEach((dimension, items) -> {
            if (!items.isEmpty()) {
                copy.put(dimension, Collections.unmodifiableSet(new TreeSet<>(items)));
            }
        });
        this.values = Collections.unmodifiableMap(copy);
    }

    /**
     * 由一个开通项的条件行构造。
     *
     * @param limits 该开通项的全部条件行
     * @return 接受条件
     */
    public static CapabilityConditions of(List<AgreementCapabilityLimit> limits) {
        Map<PricingDimension, Set<String>> values = new EnumMap<>(PricingDimension.class);
        limits.forEach(limit -> values.computeIfAbsent(limit.getLimitCode(), key -> new TreeSet<>()).add(limit.getLimitValue()));
        return new CapabilityConditions(values);
    }

    /** 有条件的维度到取值（字典序），按维度登记顺序。 */
    public Map<PricingDimension, Set<String>> values() {
        return values;
    }

    /** 某维度的取值；未限制为空集合。 */
    public Set<String> values(PricingDimension dimension) {
        return values.getOrDefault(dimension, Set.of());
    }

    /** 某维度是否有条件。 */
    public boolean constrains(PricingDimension dimension) {
        return values.containsKey(dimension);
    }

    /** 某维度的事实值是否被接受：未限制或取值在条件内；事实为空且该维度有条件时不接受。 */
    public boolean allows(PricingDimension dimension, String value) {
        return !constrains(dimension) || value != null && values(dimension).contains(value);
    }

    /**
     * 与定价画像条件相容：画像每个有条件的维度（CURRENCY 除外，由价格币种单独判断）与开通项该维度取值有交集；开通项未写的维度视为全集
     *
     * @param profileConditions 画像维度到取值
     * @return 是否相容
     */
    public boolean compatibleWith(Map<PricingDimension, Set<String>> profileConditions) {
        for (Map.Entry<PricingDimension, Set<String>> entry : profileConditions.entrySet()) {
            if (entry.getKey() == PricingDimension.CURRENCY || !constrains(entry.getKey())) {
                continue;
            }
            if (Collections.disjoint(entry.getValue(), values(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 除 CURRENCY 外各条件维度取值的全部组合（完整性检查的条件空间）
     *
     * @param limit 组合数上限
     * @return 每个组合为维度到单值；没有其他维度时为一个空组合；超过上限返回 null
     */
    public List<Map<PricingDimension, String>> combinations(int limit) {
        List<Map<PricingDimension, String>> combinations = List.of(Map.of());
        for (Map.Entry<PricingDimension, Set<String>> entry : values.entrySet()) {
            if (entry.getKey() == PricingDimension.CURRENCY) {
                continue;
            }
            if ((long) combinations.size() * entry.getValue().size() > limit) {
                return null;
            }
            combinations = expand(combinations, entry.getKey(), entry.getValue());
        }
        return combinations;
    }

    /** 把已有组合按某维度的每个取值展开。 */
    static List<Map<PricingDimension, String>> expand(List<Map<PricingDimension, String>> combinations,
        PricingDimension dimension, Set<String> dimensionValues) {
        return combinations.stream().flatMap(base -> dimensionValues.stream().map(value -> {
            Map<PricingDimension, String> next = new EnumMap<>(PricingDimension.class);
            next.putAll(base);
            next.put(dimension, value);
            return (Map<PricingDimension, String>) next;
        })).toList();
    }
}
