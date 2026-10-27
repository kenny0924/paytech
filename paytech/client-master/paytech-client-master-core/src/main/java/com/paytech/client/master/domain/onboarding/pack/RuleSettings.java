package com.paytech.client.master.domain.onboarding.pack;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.client.master.core.dal.dataobject.RuleSetting;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 规则开关解释器（design 10.0.1a 第 1 类插件位）：代码只解释已登记的 rule_code，取值来自数据
 * <li>解析顺序：精确 scope_code → 同层 * 默认 → RuleCode 内置默认。</li>
 * <li>未登记的 rule_code 忽略并告警；已登记代码的取值无法解析时失败关闭（配置错误）。</li>
 */
public final class RuleSettings {
    private static final Logger LOG = LoggerFactory.getLogger(RuleSettings.class);
    private static final String DEFAULT_SCOPE = "*";

    private final Map<String, String> values = new HashMap<>();

    /** 从 ACTIVE 规则行构建；未登记或作用层级不符的行被忽略。 */
    public RuleSettings(List<RuleSetting> rows) {
        for (RuleSetting row : rows) {
            RuleCode code = RuleCode.fromCode(row.getRuleCode());
            if (code == null || code.getScopeType() != row.getScopeType()) {
                LOG.warn("Ignored unregistered rule setting {} on {}:{}", row.getRuleCode(), row.getScopeType(), row.getScopeCode());
                continue;
            }
            values.put(key(code, row.getScopeCode()), row.getRuleValue());
        }
    }

    /**
     * 规则文本取值
     *
     * @param code 已登记规则
     * @param scopeCode 精确作用对象代码
     * @return 精确值、同层默认值或内置默认值；均没有时为 null
     */
    public String value(RuleCode code, String scopeCode) {
        String exact = scopeCode == null ? null : values.get(key(code, scopeCode));
        if (exact != null) {
            return exact;
        }
        String fallback = values.get(key(code, DEFAULT_SCOPE));
        return fallback != null ? fallback : code.getDefaultValue();
    }

    /** 0/1 开关；未配置且无默认时为 false。 */
    public boolean flag(RuleCode code, String scopeCode) {
        String value = value(code, scopeCode);
        if (value == null) {
            return false;
        }
        if (!"0".equals(value) && !"1".equals(value)) {
            throw invalid(code, scopeCode);
        }
        return "1".equals(value);
    }

    /** 是否显式配置了该规则（精确或同层默认），不含内置默认。 */
    public boolean configured(RuleCode code, String scopeCode) {
        return (scopeCode != null && values.containsKey(key(code, scopeCode))) || values.containsKey(key(code, DEFAULT_SCOPE));
    }

    /** 非负整数取值。 */
    public int number(RuleCode code, String scopeCode) {
        String value = value(code, scopeCode);
        if (value == null || !value.matches("[0-9]{1,9}")) {
            throw invalid(code, scopeCode);
        }
        return Integer.parseInt(value);
    }

    /** 非负十进制取值。 */
    public BigDecimal decimal(RuleCode code, String scopeCode) {
        String value = value(code, scopeCode);
        if (value == null || !value.matches("[0-9]{1,9}(\\.[0-9]{1,6})?")) {
            throw invalid(code, scopeCode);
        }
        return new BigDecimal(value);
    }

    /** 组合键。 */
    private static String key(RuleCode code, String scopeCode) {
        return code.getCode() + "@" + scopeCode;
    }

    /** 已登记规则的取值不合法属于目录配置错误，失败关闭。 */
    private static ErrorException invalid(RuleCode code, String scopeCode) {
        return new ErrorException("规则开关取值不合法: " + code.getCode() + "@" + scopeCode);
    }
}
