package com.paytech.client.master.api.onboarding.response;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * CM-2 入驻表单完整渲染规格（取代 form-options）：采集 Schema、目录、组织 / 标识 / 关系类型、地址组件、关系人规则、材料槽与声明
 * <li>全部展示文案以 labelKey 给出（命名空间 onboardingSchema），客户端按语言包渲染。</li>
 * <li>字段键 fieldKey 等于草稿 values 的 分区.字段 路径（IDENTIFIER 为 identifier.<标识类型>，DECLARATION 为 declaration.<类型>）。</li>
 */
@Getter
@Setter
public class FormSchemaResponseBody {
    /** Schema 版本 ID，十进制字符串。 */
    private String schemaId;

    /** Schema 版本号。 */
    private int versionNo;

    /** 辖区代码。 */
    private String jurisdictionCode;

    /** 主主体类型 INDIVIDUAL 或 ORGANIZATION。 */
    private String partyType;

    /** Schema 的组织类型键；* 为该辖区该主体类型的默认 Schema。 */
    private String organizationTypeCode;

    /** 分区与字段，按展示顺序。 */
    private List<Section> sections;

    /**
     * 目录代码到可选项；含 SELECT 字段、地址组件与属性引用的本服务目录，组织类型以 ORGANIZATION_TYPE 出现
     * <li>不含以 REF_ 开头的 Platform 参考数据目录（REF_CURRENCY 币种、REF_COUNTRY_SUBDIVISION 一级行政区）：
     * 字段与地址组件的 catalogCode 仍为 REF_ 码，选项由 client-console-bff 向 Platform 参考数据查询后补全。</li>
     * <li>国家字段（COUNTRY 控件、地址国家）不引用目录，选项同样由 client-console-bff 取 Platform ACTIVE 国家。</li>
     * <li>Schema 引用 COMMERCIAL_PAYMENT_METHOD 时含该目录：选项为 commercial 产品能力目录中 ACTIVE 的支付方式，code 为 capability_code，
     * labelKey 为 onboardingSchema.paymentMethod.&lt;代码&gt;，顺序为 commercial 返回顺序；commercial 不可用时整个响应 503。</li>
     */
    private Map<String, List<CatalogOption>> catalogs;

    /** 当前主体类型可选的组织类型（ORGANIZATION 为法人形态，INDIVIDUAL 为个人经营形态）。 */
    private List<CatalogOption> organizationTypes;

    /** Schema 引用的标识类型规则。 */
    private List<IdentifierTypeOption> identifierTypes;

    /** 辖区本地地址格式（地址所在国家等于辖区国家时使用）。 */
    private AddressFormat addressFormat;

    /** 境外地址使用的通用格式 GENERIC。 */
    private AddressFormat fallbackAddressFormat;

    /** 可用的关系类型（组织申请的人员关系）。 */
    private List<RelationTypeOption> relationTypes;

    /** 组织关系人门槛（规则开关解析结果）；个人申请为空。 */
    private RelationRules relationRules;

    /** compliance 程序解析的材料槽；组织申请含关联人员程序（subjectRole 为 ANY_RELATED 或关系角色）。 */
    private List<Material> materials;

    /** Schema 要求的声明。 */
    private List<Declaration> declarations;

    /** 一个分区。 */
    @Getter
    @Setter
    public static class Section {
        /** 分区代码：BASIC、BUSINESS、PEOPLE、MATERIALS、DECLARATIONS。 */
        private String section;

        /** 分区标题文案键。 */
        private String labelKey;

        /** 分区内分组。 */
        private List<Group> groups;
    }

    /** 分区内的一个分组。 */
    @Getter
    @Setter
    public static class Group {
        /** 分组代码，如 IDENTITY、REGISTRATION、ADDRESS。 */
        private String group;

        /** 分组标题文案键。 */
        private String labelKey;

        /** 字段。 */
        private List<Field> fields;
    }

    /** 一个字段。 */
    @Getter
    @Setter
    public static class Field {
        /** 字段键。 */
        private String fieldKey;

        /**
         * 控件代码：TEXT、TEXTAREA、DATE、COUNTRY、SELECT、MULTI_SELECT、IDENTIFIER、ADDRESS、NAME、PERSON_LIST、DOCUMENT_SLOT、DECLARATION、URL、MONEY、EMAIL、PHONE、BOOLEAN。
         * <li>MULTI_SELECT 的草稿取值为英文逗号分隔的代码串（大写、去重、按代码升序，如 APPLE_PAY,CARD）。</li>
         */
        private String widget;

        /** SELECT / MULTI_SELECT 取值目录代码（见 catalogs；REF_ 开头时为 Platform 参考数据目录，不在 catalogs 中），可空。 */
        private String catalogCode;

        /** 要求级别：REQUIRED、EVENTUALLY、OPTIONAL（HIDDEN 不返回）。 */
        private String requirementLevel;

        /** 二选一组代码，同组至少满足一个；可空。 */
        private String requirementGroup;

        /** 二选一组的标题文案键，可空。 */
        private String requirementGroupLabelKey;

        /** 适用主体：PRIMARY、RELATED_PERSON、BOTH。 */
        private String appliesTo;

        /** 校验规则。 */
        private Validation validation;

        /** 可见条件 field_key=value，可空。 */
        private String visibleWhen;

        /** 标题文案键。 */
        private String labelKey;

        /** 帮助文案键，可空。 */
        private String helpKey;

        /** 展示顺序。 */
        private int order;
    }

    /** 字段校验规则。 */
    @Getter
    @Setter
    public static class Validation {
        /** 完整匹配的正则，可空。 */
        private String pattern;

        /** 最少字符数，可空。 */
        private Integer minLength;

        /** 最多字符数，可空。 */
        private Integer maxLength;

        /** 标识号码的校验位算法（IDENTIFIER 字段），可空。 */
        private String checksumRule;
    }

    /** 目录项。 */
    @Getter
    @Setter
    public static class CatalogOption {
        /** 代码。 */
        private String code;

        /** 展示文案键。 */
        private String labelKey;
    }

    /** 标识类型规则。 */
    @Getter
    @Setter
    public static class IdentifierTypeOption {
        /** 标识类型代码。 */
        private String code;

        /** 适用主体类型。 */
        private String partyType;

        /** 展示文案键。 */
        private String labelKey;

        /** 规范化后号码须匹配的正则，可空。 */
        private String pattern;

        /** 校验位算法：NONE、LUHN、EG_NID。 */
        private String checksumRule;

        /** 唯一性作用域：NONE、COUNTRY（须填签发国）、AUTHORITY（按组织登记机关）。 */
        private String issuerScopeMode;

        /** 是否必须填写到期日。 */
        private boolean expiryRequired;

        /** 获取方式：MANUAL_ENTRY 或 VERIFIED_SESSION（本期只有 MANUAL_ENTRY）。 */
        private String acquireMode;
    }

    /** 地址格式。 */
    @Getter
    @Setter
    public static class AddressFormat {
        /** 地址格式代码。 */
        private String code;

        /** 组件，按展示顺序。 */
        private List<AddressComponent> components;
    }

    /** 地址组件。 */
    @Getter
    @Setter
    public static class AddressComponent {
        /** 组件，即草稿 address 分区字段名。 */
        private String component;

        /** 是否必填（已叠加 POSTAL_CODE_REQUIRED 规则）。 */
        private boolean required;

        /** 取值目录代码（REF_ 开头时为 Platform 参考数据目录，不在 catalogs 中），可空。 */
        private String catalogCode;

        /** 标题文案键。 */
        private String labelKey;

        /** 展示顺序。 */
        private int order;
    }

    /** 关系类型。 */
    @Getter
    @Setter
    public static class RelationTypeOption {
        /** 关系类型代码。 */
        private String code;

        /** 规则角色：OWNER、UBO、CONTROL、DIRECTOR、MANAGER、SIGNATORY、OTHER。 */
        private String ruleRole;

        /** 是否计入受益人门槛。 */
        private boolean uboFlag;

        /** 是否须填持股比例。 */
        private boolean ownershipPctRequired;

        /** 展示文案键。 */
        private String labelKey;
    }

    /** 组织关系人门槛。 */
    @Getter
    @Setter
    public static class RelationRules {
        /** 受益人持股门槛百分比，十进制字符串。 */
        private String uboThresholdPct;

        /** 兜底规则：CONTROL_THEN_SMO 或 NONE。 */
        private String uboFallbackRule;

        /** 无论持股都需控制人。 */
        private boolean controlPersonRequired;

        /** 最少签字人数。 */
        private int minSignatories;

        /** 须有经理。 */
        private boolean requiresManager;

        /** 须有董事或经理。 */
        private boolean requiresDirectors;

        /** 受益人采集方式：OWNERS_AND_CONTROL、CONTROL_ONLY、EXEMPT。 */
        private String uboCollectionMode;

        /** 持股与门槛的比较方式：GTE 达到门槛即计入（或以上），GT 须超过门槛。 */
        private String uboThresholdComparison;
    }

    /** 材料槽。 */
    @Getter
    @Setter
    public static class Material {
        /** 材料槽代码。 */
        private String requirementCode;

        /** 适用主体：PRIMARY、ANY_RELATED 或关系角色。 */
        private String subjectRole;

        /** 接受的材料类型。 */
        private List<String> documentTypes;

        /** 是否必交。 */
        private boolean required;

        /** 签发日起最长天数，可空。 */
        private Integer maxAgeDays;
    }

    /** 声明要求。 */
    @Getter
    @Setter
    public static class Declaration {
        /** 声明类型。 */
        private String declarationType;

        /** 声明文案键。 */
        private String labelKey;

        /** 要求级别。 */
        private String requirementLevel;

        /**
         * 声明所附文档（条款、告知）的当前版本
         * <li>不为空时，写入声明须以 termsVersion 携带此值；为空表示声明不附版本化文档。</li>
         */
        private String documentVersion;
    }
}
