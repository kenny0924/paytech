package com.paytech.commercial.api.checkout.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 完整的 Checkout 商业上下文；Commerce 在创建订单时冻结，查询本身不保存选择。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCheckoutContextResponseBody {
    /** Commerce 本次创建冻结的商业时点；原样回显，重试不得改成当前时间。 */
    private String contextAsOfTime;

    /** CCT 本次检查的 UTC 时刻，最多六位小数。 */
    private String evaluatedAt;

    /** 已授权 Client 的规范正十进制 BIGINT 字符串。 */
    private String clientId;

    /** Commerce 原订单编号，最长 64 个字符。 */
    private String orderNo;

    /** 选定 OE 的不可变五位编号。 */
    private String entityNo;

    /** OE 来源 Code：CLIENT 或 PARTNER。 */
    private String sourceType;

    /** Client 唯一 Primary 经营分类。 */
    private Classification classification;

    /** 准确授权、范围及来源引用。 */
    private Eligibility eligibility;

    /** 精确 Client/OE 合同和条款版本。 */
    private ClientTerms clientTerms;

    /** Partner 合同和双条款；非 PARTNER 来源时为 null。 */
    private PartnerTerms partnerTerms;

    /** 法律文件、卖方披露和参与方资料引用。 */
    private Legal legal;

    /** 按合同业务模式派生、由收单冻结的四项主体。 */
    private Derived derived;

    /** 返回前复核的相关业务根修订。 */
    private Revisions revisions;

    /**
     * 商业关系快照
     * <li>由选中候选的事实推导；收单按 effectiveModelVersion 决定下游是否按快照生效，0 表示只记录。</li>
     */
    private RelationshipSnapshot relationshipSnapshot;

    /** Client 经营分类证据。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Classification {
        /** Client 已批准的业务资料 ID。 */
        private String businessProfileId;

        /** 唯一 Primary 分类 ID。 */
        private String classificationId;

        /** Primary 行业代码。 */
        private String industryCode;

        /** Primary 四位 MCC 代码。 */
        private String mccCode;
    }

    /** 准确 OE 准入引用。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Eligibility {
        /** 选定 Client/OE 关联 ID。 */
        private String affiliationId;

        /** 关联优先级；正整数，越小越优先。 */
        private int priorityNo;

        /** 批准的准确四要素 Scope ID。 */
        private String entityScopeId;

        /** 选定 OE 来源关系 ID。 */
        private String entitySourceRelationId;
    }

    /** Client 合同与定价条款。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientTerms {
        /** 适用且已签署的合同 ID。 */
        private String agreementId;

        /** 合同对应的商业条款计划 ID。 */
        private String planId;

        /** 商业时点适用的不可变条款版本 ID。 */
        private String versionId;

        /** 合同业务模式 Code：MOR 或 PSP。 */
        private String businessMode;

        /** 合同资金模式 Code；本期只会返回 SETTLE_THROUGH_PLATFORM。 */
        private String fundMode;

        /** 合同定价权归属 Code：PLATFORM 或 PARTNER。 */
        private String pricingAuthority;

        /** 推荐该合同的 Local Partner ID；没有推荐关系时为 null。 */
        private String referralPartnerId;

        /** 合同订阅的产品 Code，按 Code 升序；必含 ACQUIRING。 */
        private java.util.List<String> agreementProducts;

        /**
         * 本次支付方式命中、且有生效价格的全部开通项，按开通项代码升序
         * <li>请求给出支付方式时至少一项；未给时为全部可用的 PAYMENT_METHOD 开通项，可为空数组。接受条件与 3DS 要求由收单在执行时使用（二期）。</li>
         */
        private java.util.List<Capability> capabilities;
    }

    /** 命中的合同开通项。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capability {
        /** 开通项 ID。 */
        private String agreementCapabilityId;

        /** 可开通项代码，如 CARD、APPLE_PAY。 */
        private String capabilityCode;

        /** 开通项状态 Code；命中时恒为 ENABLED。 */
        private String status;

        /** 已废弃，恒为 null（细分维度改为 conditions）。 */
        private String dimensionCode;

        /** 已废弃，恒为 null。 */
        private String dimensionValue;

        /** 滚动保证金比例，0 至 1 的十进制字符串；未约定时为 null。 */
        private String rollingReserveRate;

        /** 滚动保证金天数；未约定时为 null。 */
        private Integer rollingReserveDays;

        /** 可开通项绑定的支付方式代码，如 CARD；非支付方式类为 null。 */
        private String paymentMethodCode;

        /** 可开通项种类 Code：PAYMENT_METHOD、FEATURE 等。 */
        private String capabilityKind;

        /** 接受条件，按维度代码排序：同维度多值 OR，异维度 AND；未写的维度不限。 */
        private java.util.List<CapabilityCondition> conditions;

        /** 是否要求 3DS 认证。 */
        private boolean require3ds;
    }

    /** 开通项的一个接受条件。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapabilityCondition {
        /** 维度 Code，如 CURRENCY、CARD_BRAND。 */
        private String dimensionCode;

        /** 取值，按字典序。 */
        private java.util.List<String> values;
    }

    /**
     * D02 四项派生
     * <li>MOR：卖方为 OE Party，税务责任 OPERATING_ENTITY；PSP：卖方为 Client Party，税务责任 CLIENT。</li>
     * <li>资金经平台结算时资金账簿恒归收单 OE（OPERATING_ENTITY）。</li>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Derived {
        /** 卖方 Party ID。 */
        private String sellerPartyId;

        /** 收单 OE 编号，与顶层 entityNo 相同。 */
        private String acquiringEntityNo;

        /** 税务责任方类型 Code：OPERATING_ENTITY 或 CLIENT。 */
        private String taxResponsibleParty;

        /** 资金账簿归属类型 Code：OPERATING_ENTITY；账簿主体即 acquiringEntityNo。 */
        private String fundsBookOwner;
    }

    /** Partner 来源专用商业条款。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerTerms {
        /** 来源 Local Partner ID。 */
        private String localPartnerId;

        /** 适用且已签署的合同 ID。 */
        private String agreementId;

        /** Partner 合同覆盖该 OE 的关联 ID。 */
        private String agreementOperatingEntityId;

        /** Partner 分成计划 ID。 */
        private String sharePlanId;

        /** Partner 分成条款版本 ID。 */
        private String shareVersionId;

        /** 平台服务费计划 ID。 */
        private String serviceChargePlanId;

        /** 平台服务费条款版本 ID。 */
        private String serviceChargeVersionId;

        /** Partner 合同唯一平台签约 Party ID。 */
        private String platformSignatoryPartyId;
    }

    /** 法律文件证据。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileEvidence {
        /** 法律文档 ID。 */
        private String documentId;

        /** 不可变文件 ID。 */
        private String fileId;

        /** 文件内容 SHA-256，小写十六进制。 */
        private String contentHash;
    }

    /** 合同参与方资料证据。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartyEvidence {
        /** 合同参与方 ID。 */
        private String partyId;

        /** 签署时引用的批准资料 ID。 */
        private String partyProfileId;

        /** 原批准资料的内容 Hash。 */
        private String profileContentHash;
    }

    /** 法律资料的不可变引用。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Legal {
        /** 选定 Client/OE 合同 ID。 */
        private String clientAgreementId;

        /** 消费者条款文档 ID。 */
        private String consumerTermsDocumentId;

        /** 退款政策文档 ID。 */
        private String refundPolicyDocumentId;

        /** 隐私声明文档 ID。 */
        private String privacyNoticeDocumentId;

        /** 准确国家及 Locale 的卖方披露 ID。 */
        private String sellerDisclosureId;

        /** 三份法律文档的不可变文件证据，不包含正文。 */
        private java.util.List<FileEvidence> documents;

        /** 按参与方去重的已批准资料证据。 */
        private java.util.List<PartyEvidence> parties;

        /** 选中卖方披露向消费者公开的卖方文本，原样透传 client-master。 */
        private Seller seller;
    }

    /** 卖方披露的公开文本。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Seller {
        /** 卖方法定名称。 */
        private String legalName;

        /** 卖方注册编号。 */
        private String registrationNumber;

        /** 卖方税务登记编号；不披露时为 null。 */
        private String taxRegistrationNumber;

        /** 卖方地址文本。 */
        private String addressText;

        /** 卖方支持邮箱。 */
        private String supportEmail;

        /** 卖方支持电话；不披露时为 null。 */
        private String supportPhone;
    }

    /**
     * 商业关系快照（决定 9.2）
     * <li>本轮只对 OE 来源 PARTNER 推导：MOR 合同对应 C1，PSP 合同对应 C2。</li>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipSnapshot {
        /**
         * 生效模型版本
         * <li>0：快照只记录，下游沿用旧字段；1：快照生效。由 commercial 开关 commercial.relationship-snapshot.enabled 决定。</li>
         */
        private int effectiveModelVersion;

        /** 推导规则版本；本轮恒为 1。 */
        private int snapshotModelVersion;

        /** 资金流向 Code，见 FundFlow；本轮恒为 THROUGH_CUSTODIAN。 */
        private String fundFlow;

        /** 合同业务模式 Code：MOR 或 PSP，与 clientTerms.businessMode 相同。 */
        private String businessMode;

        /**
         * 角色绑定
         * <li>按 RelationshipRole 声明顺序排列，同一角色至多一行。</li>
         * <li>MOR 产出 8 行（无 ACQUIRER_SUB_MERCHANT），PSP 产出 9 行。</li>
         */
        private java.util.List<RoleBinding> bindings;
    }

    /** 一个角色到主体的绑定。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleBinding {
        /** 角色 Code，见 RelationshipRole。 */
        private String role;

        /** 主体类型 Code，见 RelationshipSubjectType。 */
        private String subjectType;

        /**
         * 主体标识
         * <li>CLIENT 为 clientId，OPERATING_ENTITY 为 entityNo，LOCAL_PARTNER 为 localPartnerId；PLATFORM 为 null。</li>
         */
        private String subjectId;

        /** 主体法律主体 Party ID；规范正十进制字符串，可空。 */
        private String partyId;
    }

    /** CCT 输出前复核的业务根修订。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Revisions {
        /** Client 业务根修订。 */
        private String clientChangeVersion;

        /** OE 业务根修订。 */
        private String entityChangeVersion;

        /** Client 合同根修订。 */
        private String clientAgreementChangeVersion;

        /** Partner 合同根修订；非 PARTNER 来源时为 null。 */
        private String partnerAgreementChangeVersion;
    }
}
