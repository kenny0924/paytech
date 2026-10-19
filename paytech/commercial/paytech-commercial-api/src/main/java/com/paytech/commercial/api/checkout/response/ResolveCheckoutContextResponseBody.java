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

    /** OE 来源 Code：PLATFORM、CLIENT 或 PARTNER。 */
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

        /** 本次支付方式命中的开通项；请求未给支付方式时为 null。 */
        private Capability capability;
    }

    /** 命中的合同开通项。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capability {
        /** 开通项 ID。 */
        private String agreementCapabilityId;

        /** 可开通项代码，如 CARD_VISA、APPLE_PAY。 */
        private String capabilityCode;

        /** 开通项状态 Code；命中时恒为 ENABLED。 */
        private String status;

        /** 滚动保证金比例，0 至 1 的十进制字符串；未约定时为 null。 */
        private String rollingReserveRate;

        /** 滚动保证金天数；未约定时为 null。 */
        private Integer rollingReserveDays;
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
