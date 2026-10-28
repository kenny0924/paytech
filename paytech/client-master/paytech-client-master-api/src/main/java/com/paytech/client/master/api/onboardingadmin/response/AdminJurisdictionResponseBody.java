package com.paytech.client.master.api.onboardingadmin.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** CM-10 辖区包只读配置：辖区、规则开关、目录与本地地址格式组件。 */
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

    /**
     * 辖区、其组织类型与标识类型作用域的规则开关（含 * 默认）
     * <li>另含该辖区全部 Schema 版本（含已退役）引用的声明类型（DECLARATION）与扩展属性（ATTRIBUTE）规则，如 DOCUMENT_VERSION、DATE_NOT_PAST。</li>
     */
    private List<RuleSettingItem> rules;

    /** 组织类型（全部状态）。 */
    private List<OrganizationTypeItem> organizationTypes;

    /** 辖区与全局标识类型（全部状态）。 */
    private List<IdentifierTypeItem> identifierTypes;

    /** 辖区与全局关系类型（全部状态）。 */
    private List<RelationTypeItem> relationTypes;

    /** 本地地址格式（addressFormatCode）的组件，按展示顺序；未配置格式时为空列表。 */
    private List<AddressComponentItem> addressComponents;

    /** 一条规则开关。 */
    @Getter
    @Setter
    public static class RuleSettingItem {
        /** 作用层级：JURISDICTION、ORGANIZATION_TYPE、IDENTIFIER_TYPE、DECLARATION、ATTRIBUTE。 */
        private String scopeType;

        /** 作用对象代码：辖区码、辖区码:组织类型码、标识类型码、声明类型码、属性码，或 * 默认。 */
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

    /** 一个地址格式组件。 */
    @Getter
    @Setter
    public static class AddressComponentItem {
        /** 组件，即草稿 address 分区字段名，如 stateOrProvince。 */
        private String component;

        /** 取值目录代码，可空（自由文本）；REF_ 开头为 Platform 参考数据目录（如 REF_COUNTRY_SUBDIVISION），不在本服务目录中。 */
        private String catalogCode;

        /** 组件自身的必填标记；未叠加 POSTAL_CODE_REQUIRED 规则（规则见 rules）。 */
        private boolean required;

        /** 展示顺序，数值越小越靠前。 */
        private int order;
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
