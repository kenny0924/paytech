package com.paytech.risk.decision.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Risk 规则配置与业务字典的公开业务错误，HTTP 状态由统一解析器映射。 */
public enum RiskDecisionConfigurationErrorCode implements CodeMessageEnum {
    RISK_DECISION_CONFIGURATION_NOT_FOUND(
        "RISK_DECISION_CONFIGURATION_NOT_FOUND", "Risk 决策配置不存在"),

    RISK_RULE_SET_IN_USE("RISK_RULE_SET_IN_USE", "规则集正在使用，无法删除"),

    RISK_RULE_SET_CHANGED("RISK_RULE_SET_CHANGED", "规则集配置已变化，请刷新后重新确认"),

    RISK_RULE_SET_DELETED("RISK_RULE_SET_DELETED", "规则集已删除，编码已保留，请使用新编码"),

    RISK_FLOW_CODE_EXISTS("RISK_FLOW_CODE_EXISTS", "流程编码已存在"),

    RISK_FLOW_VERSION_CONFLICT("RISK_FLOW_VERSION_CONFLICT", "流程版本已变化，请刷新后重新确认"),

    RISK_FLOW_DELETED("RISK_FLOW_DELETED", "流程已删除"),

    RISK_FLOW_REFERENCE_UNAVAILABLE("RISK_FLOW_REFERENCE_UNAVAILABLE", "规则版本或原因资格已变化"),

    RISK_DICTIONARY_NOT_FOUND("RISK_DICTIONARY_NOT_FOUND", "字典代码未登记"),

    RISK_DICTIONARY_CODE_EXISTS("RISK_DICTIONARY_CODE_EXISTS", "字典编码已存在，请查询后确认"),

    RISK_DICTIONARY_CHANGED("RISK_DICTIONARY_CHANGED", "字典已由其他操作更新，请重新载入后确认"),

    RISK_DICTIONARY_SELECTION_UNAVAILABLE("RISK_DICTIONARY_SELECTION_UNAVAILABLE", "所选字典代码未登记或已停用，请重新选择");

    private final String code;
    private final String message;

    RiskDecisionConfigurationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
