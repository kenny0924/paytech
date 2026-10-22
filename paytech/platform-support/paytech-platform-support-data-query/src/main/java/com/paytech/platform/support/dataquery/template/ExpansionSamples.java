package com.paytech.platform.support.dataquery.template;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;

/** 展开校验用的样例取值：每个参数的候选值，以及满足声明约束的单个样例。 */
final class ExpansionSamples {

    /** 工具类，不实例化。 */
    private ExpansionSamples() {
    }

    /** 样例游标：任何列名都取 1，模拟“有上一页”。 */
    static final class SampleCursor extends AbstractMap<String, Object> {

        /**
         * 任意列名都返回 1。
         *
         * @param key 列名
         * @return 1
         */
        @Override
        public Object get(Object key) {
            return 1L;
        }

        /**
         * 不枚举具体列。
         *
         * @return 空集合
         */
        @Override
        public Set<Entry<String, Object>> entrySet() {
            return Set.of();
        }
    }

    /**
     * 一个参数的候选值：集合取“空 / 一个元素”，片段取每个可选值，布尔取 true / false，枚举取每个允许值，
     * 其余取一个样例；可选且无默认值时再加 null。
     *
     * @param declaration 参数声明
     * @return 候选值，第一个非空样例在前
     */
    static List<Object> options(QueryParameterDeclaration declaration) {
        if (declaration.getType() == QueryParameterType.LIST) {
            return List.of(List.of(), List.of(sample(declaration.elementDeclaration())));
        }
        List<Object> options = new ArrayList<>();
        switch (declaration.getType()) {
            case FRAGMENT -> declaration.getChoices()
                .forEach((key, text) -> options.add(new SelectedFragment(key, text)));
            case BOOLEAN -> options.addAll(List.of(Boolean.TRUE, Boolean.FALSE));
            case ENUM -> options.addAll(declaration.getAllowedValues());
            default -> options.add(sample(declaration));
        }
        if (!declaration.isRequired() && declaration.getDefaultValue() == null) {
            options.add(null);
        }
        return options;
    }

    /**
     * 满足声明约束的单个样例。
     *
     * @param declaration 声明（可以是 LIST 元素或对象字段）
     * @return 样例
     */
    static Object sample(QueryParameterDeclaration declaration) {
        return switch (declaration.getType()) {
            case STRING, ENUM -> declaration.getAllowedValues() == null || declaration.getAllowedValues().isEmpty()
                ? "x" : declaration.getAllowedValues().getFirst();
            case INTEGER -> bounded(declaration, 1L);
            case DECIMAL -> bounded(declaration, BigDecimal.ONE);
            case BOOLEAN -> Boolean.TRUE;
            case DATE -> bounded(declaration, LocalDate.of(2026, 1, 1));
            case DATETIME -> bounded(declaration, LocalDateTime.of(2026, 1, 1, 0, 0));
            case FRAGMENT -> options(declaration).getFirst();
            case LIST -> List.of(sample(declaration.elementDeclaration()));
            case OBJECT -> sampleObject(declaration);
        };
    }

    /**
     * 有下限取下限，否则有上限取上限，否则取默认样例。
     *
     * @param declaration 声明
     * @param fallback 默认样例
     * @return 样例
     */
    private static Object bounded(QueryParameterDeclaration declaration, Object fallback) {
        String bound = declaration.getMin() != null ? declaration.getMin() : declaration.getMax();
        Object value = bound == null ? null : ParameterValueConverter.parseText(declaration.getType(), bound);
        return value == null ? fallback : value;
    }

    /**
     * 对象样例：每个字段取其样例。
     *
     * @param declaration 对象声明
     * @return 保持字段顺序的不可变 Map
     */
    private static Object sampleObject(QueryParameterDeclaration declaration) {
        Map<String, Object> object = new LinkedHashMap<>();
        for (QueryParameterDeclaration field : declaration.getFields()) {
            object.put(field.getName(), sample(field));
        }
        return Collections.unmodifiableMap(object);
    }
}
