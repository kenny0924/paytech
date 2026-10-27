package com.paytech.client.master.api.onboardingadmin.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** CM-10 辖区包只读配置：辖区、规则开关与目录。 */
@Getter
@Setter
public class AdminJurisdictionResponseBody {
    /** 辖区代码。 */
    private String jurisdictionCode;

    /** 国家代码。 */
    private String countryCode;

    /** 展示文案键。 */
    private String labelKey;

    /** 本地地址格式代码。 */
    private String addressFormatCode;

    /** 状态 DRAFT、ACTIVE、RETIRED。 */
    private String status;

    /** 目录并发版本。 */
    private long changeVersion;

    /** 辖区、其组织类型与标识类型作用域的规则开关（含 * 默认）。 */
    private List<RuleSettingItem> rules;

    /** 组织类型（全部状态）。 */
    private List<OrganizationTypeItem> organizationTypes;

    /** 辖区与全局标识类型（全部状态）。 */
    private List<IdentifierTypeItem> identifierTypes;

    /** 辖区与全局关系类型（全部状态）。 */
    private List<RelationTypeItem> relationTypes;

    /** 一条规则开关。 */
    @Getter
    @Setter
    public static class RuleSettingItem {
        /** 作用层级。 */
        private String scopeType;

        /** 作用对象代码。 */
        private String scopeCode;

        /** 规则代码。 */
        private String ruleCode;

        /** 规则取值。 */
        private String ruleValue;

        /** 状态。 */
        private String status;
    }

    /** 一个组织类型。 */
    @Getter
    @Setter
    public static class OrganizationTypeItem {
        /** 代码。 */
        private String typeCode;

        /** 文案键。 */
        private String labelKey;

        /** 展示顺序。 */
        private int displayOrder;

        /** 状态。 */
        private String status;
    }

    /** 一个标识类型。 */
    @Getter
    @Setter
    public static class IdentifierTypeItem {
        /** 代码。 */
        private String identifierTypeCode;

        /** 辖区；全局为空。 */
        private String jurisdictionCode;

        /** 主体类型。 */
        private String partyType;

        /** 文案键。 */
        private String labelKey;

        /** 格式正则。 */
        private String formatRegex;

        /** 规范化规则。 */
        private String normalizationRule;

        /** 校验位算法。 */
        private String checksumRule;

        /** 作用域方式。 */
        private String issuerScopeMode;

        /** 状态。 */
        private String status;
    }

    /** 一个关系类型。 */
    @Getter
    @Setter
    public static class RelationTypeItem {
        /** 代码。 */
        private String relationTypeCode;

        /** 辖区；全局为空。 */
        private String jurisdictionCode;

        /** 规则角色。 */
        private String ruleRole;

        /** 计入受益人。 */
        private boolean uboFlag;

        /** 须填持股比例。 */
        private boolean ownershipPctRequired;

        /** 文案键。 */
        private String labelKey;

        /** 状态。 */
        private String status;
    }
}
