package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * client-master 已登记并读取的规则开关代码（design 10.1 规则注册表）
 * <li>每个代码对应一段读取它的代码逻辑；新规则类型 = 在此登记 + 增加读取分支，不改表。</li>
 * <li>t_rule_setting 中未在此登记的代码被忽略并告警；内置默认值只在辖区包没有配置时使用。</li>
 */
public enum RuleCode implements CodeEnum {
    /** 地址邮编是否必填（0/1）；覆盖辖区本地地址格式中邮编组件的必填标记。 */
    POSTAL_CODE_REQUIRED(RuleScopeType.JURISDICTION, null),

    /** 最终受益人持股门槛百分比。 */
    UBO_THRESHOLD_PCT(RuleScopeType.JURISDICTION, "25"),

    /** 没有达到门槛的受益人时的兜底规则：CONTROL_THEN_SMO 允许以控制人或高级管理人员兜底，NONE 不兜底。 */
    UBO_FALLBACK_RULE(RuleScopeType.JURISDICTION, "NONE"),

    /** 无论持股都必须有控制人（0/1）。 */
    CONTROL_PERSON_REQUIRED(RuleScopeType.JURISDICTION, "0"),

    /** 个人最低年龄。 */
    MIN_AGE(RuleScopeType.JURISDICTION, "18"),

    /** 辖区准入策略：OPEN、ENHANCED_ONLY、CLOSED。 */
    ACCEPTANCE_POLICY(RuleScopeType.JURISDICTION, "OPEN"),

    /** 准入受限时门户展示的文案键。 */
    ACCEPTANCE_REASON_KEY(RuleScopeType.JURISDICTION, null),

    /** 组织类型的受益人采集方式：OWNERS_AND_CONTROL、CONTROL_ONLY、EXEMPT。 */
    UBO_COLLECTION_MODE(RuleScopeType.ORGANIZATION_TYPE, "OWNERS_AND_CONTROL"),

    /** 最少签字人数。 */
    MIN_SIGNATORIES(RuleScopeType.ORGANIZATION_TYPE, "0"),

    /** 是否必须有经理（0/1）。 */
    REQUIRES_MANAGER(RuleScopeType.ORGANIZATION_TYPE, "0"),

    /** 是否必须有董事或经理（0/1）。 */
    REQUIRES_DIRECTORS(RuleScopeType.ORGANIZATION_TYPE, "0"),

    /** 组织类型对应的申请主体类型：ORGANIZATION 或 INDIVIDUAL（个人经营形态，如独资机构）。 */
    PARTY_TYPE(RuleScopeType.ORGANIZATION_TYPE, "ORGANIZATION"),

    /** 标识获取方式：MANUAL_ENTRY 或 VERIFIED_SESSION（本期只支持手工录入）。 */
    ACQUIRE_MODE(RuleScopeType.IDENTIFIER_TYPE, "MANUAL_ENTRY"),

    /** 提交时是否必须填写到期日（0/1）。 */
    EXPIRY_REQUIRED(RuleScopeType.IDENTIFIER_TYPE, "0"),

    /** 号码是否只以 Vault Token 保存（0/1）；未配置时按 1 保守处理。 */
    VAULT_REQUIRED(RuleScopeType.IDENTIFIER_TYPE, "1");

    private final RuleScopeType scopeType;
    private final String defaultValue;

    /** 作用层级与内置默认值。 */
    RuleCode(RuleScopeType scopeType, String defaultValue) {
        this.scopeType = scopeType;
        this.defaultValue = defaultValue;
    }

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /** 规则所属作用层级。 */
    public RuleScopeType getScopeType() {
        return scopeType;
    }

    /** 辖区包未配置时的内置默认值；为空表示没有默认。 */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * 按 Code 解析
     *
     * @param code 规则代码
     * @return 已登记的规则；未登记返回 null
     */
    public static RuleCode fromCode(String code) {
        for (RuleCode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
