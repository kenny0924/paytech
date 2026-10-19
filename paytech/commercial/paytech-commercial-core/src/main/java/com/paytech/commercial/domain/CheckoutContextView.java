package com.paytech.commercial.domain;

import lombok.Value;
import lombok.experimental.Accessors;

/** 同一 CCT 读快照生成的商业候选；通过资格检查和修订复核后才能返回。 */
@Value
@Accessors(fluent = true)
public class CheckoutContextView {
    /** Commerce 本次创建冻结的商业时点；原样回显，重试不得改成当前时间。 */
    String contextAsOfTime;

    /** CCT 本次检查的 UTC 时刻，最多六位小数。 */
    String evaluatedAt;

    /** 已授权 Client 的规范正十进制 BIGINT 字符串。 */
    String clientId;

    /** Commerce 原订单编号，最长 64 个字符。 */
    String orderNo;

    /** 选定 OE 的不可变五位编号。 */
    String entityNo;

    /** OE 来源 Code：PLATFORM、CLIENT 或 PARTNER。 */
    String sourceType;

    /** Client 唯一 Primary 经营分类。 */
    Classification classification;

    /** 准确授权、范围及来源引用。 */
    Eligibility eligibility;

    /** 精确 Client/OE 合同和条款版本。 */
    ClientTerms clientTerms;

    /** Partner 合同和双条款；非 PARTNER 来源时为 null。 */
    PartnerTerms partnerTerms;

    /** 法律文件、卖方披露和参与方资料引用。 */
    Legal legal;

    /** D02 四项派生：卖方 Party、收单 OE、税务责任方与资金账簿归属。 */
    Derived derived;

    /** 返回前复核的相关业务根修订。 */
    Revisions revisions;

    /** Primary 分类证据。 */
    @Value
    @Accessors(fluent = true)
    public static class Classification {
        /** Client 已批准的业务资料 ID。 */
        String businessProfileId;

        /** 唯一 Primary 分类 ID。 */
        String classificationId;

        /** Primary 行业代码。 */
        String industryCode;

        /** Primary 四位 MCC 代码。 */
        String mccCode;
    }

    /** 准确准入引用。 */
    @Value
    @Accessors(fluent = true)
    public static class Eligibility {
        /** 选定 Client/OE 关联 ID。 */
        String affiliationId;

        /** 关联优先级；正整数，越小越优先。 */
        int priorityNo;

        /** 批准的准确四要素 Scope ID。 */
        String entityScopeId;

        /** 选定 OE 来源关系 ID。 */
        String entitySourceRelationId;
    }

    /** Client 合同条款。 */
    @Value
    @Accessors(fluent = true)
    public static class ClientTerms {
        /** 适用且已签署的合同 ID。 */
        String agreementId;

        /** 合同对应的商业条款计划 ID。 */
        String planId;

        /** 商业时点适用的不可变条款版本 ID。 */
        String versionId;

        /** 合同业务模式 Code：MOR 或 PSP。 */
        String businessMode;

        /** 合同资金模式 Code；本期只会返回 SETTLE_THROUGH_PLATFORM。 */
        String fundMode;

        /** 合同定价权归属 Code：PLATFORM 或 PARTNER。 */
        String pricingAuthority;

        /** 推荐该合同的 Local Partner ID；没有推荐关系时为 null。 */
        String referralPartnerId;

        /** 合同订阅的产品 Code，按 Code 升序。 */
        java.util.List<String> agreementProducts;

        /**
         * 本次支付方式命中的全部开通项，按开通项代码升序
         * <li>请求未给支付方式时为空数组；给出时至少一项，卡品牌等维度在支付尝试时再按实际品牌选用。</li>
         */
        java.util.List<Capability> capabilities;
    }

    /** 命中的合同开通项。 */
    @Value
    @Accessors(fluent = true)
    public static class Capability {
        /** 开通项 ID。 */
        String agreementCapabilityId;

        /** 可开通项代码。 */
        String capabilityCode;

        /** 开通项状态 Code；命中时恒为 ENABLED。 */
        String status;

        /** 目录维度 Code，如 CARD_BRAND；不分维度时为 null。 */
        String dimensionCode;

        /** 目录维度值，如 VISA；不分维度时为 null。 */
        String dimensionValue;

        /** 滚动保证金比例，0 至 1 的十进制字符串；未约定时为 null。 */
        String rollingReserveRate;

        /** 滚动保证金天数；未约定时为 null。 */
        Integer rollingReserveDays;
    }

    /** 按合同业务模式派生、由收单冻结的四项主体。 */
    @Value
    @Accessors(fluent = true)
    public static class Derived {
        /** 卖方 Party ID：MOR 为 OE Party，PSP 为 Client Party。 */
        String sellerPartyId;

        /** 收单 OE 编号。 */
        String acquiringEntityNo;

        /** 税务责任方类型 Code：MOR 为 OPERATING_ENTITY，PSP 为 CLIENT。 */
        String taxResponsibleParty;

        /** 资金账簿归属类型 Code；资金经平台结算时恒为 OPERATING_ENTITY，账簿主体即 acquiringEntityNo。 */
        String fundsBookOwner;
    }

    /** Partner 商业条款。 */
    @Value
    @Accessors(fluent = true)
    public static class PartnerTerms {
        /** 来源 Local Partner ID。 */
        String localPartnerId;

        /** 适用且已签署的合同 ID。 */
        String agreementId;

        /** Partner 合同覆盖该 OE 的关联 ID。 */
        String agreementOperatingEntityId;

        /** Partner 分成计划 ID。 */
        String sharePlanId;

        /** Partner 分成条款版本 ID。 */
        String shareVersionId;

        /** 平台服务费计划 ID。 */
        String serviceChargePlanId;

        /** 平台服务费条款版本 ID。 */
        String serviceChargeVersionId;

        /** Partner 合同唯一平台签约 Party ID。 */
        String platformSignatoryPartyId;
    }

    /** 法律文件证据。 */
    @Value
    @Accessors(fluent = true)
    public static class FileEvidence {
        /** 法律文档 ID。 */
        String documentId;

        /** 不可变文件 ID。 */
        String fileId;

        /** 文件内容 SHA-256，小写十六进制。 */
        String contentHash;
    }

    /** 参与方资料证据。 */
    @Value
    @Accessors(fluent = true)
    public static class PartyEvidence {
        /** 合同参与方 ID。 */
        String partyId;

        /** 签署时引用的批准资料 ID。 */
        String partyProfileId;

        /** 原批准资料的内容 Hash。 */
        String profileContentHash;
    }

    /** 法律材料引用。 */
    @Value
    @Accessors(fluent = true)
    public static class Legal {
        /** 选定 Client/OE 合同 ID。 */
        String clientAgreementId;

        /** 消费者条款文档 ID。 */
        String consumerTermsDocumentId;

        /** 退款政策文档 ID。 */
        String refundPolicyDocumentId;

        /** 隐私声明文档 ID。 */
        String privacyNoticeDocumentId;

        /** 准确国家及 Locale 的卖方披露 ID。 */
        String sellerDisclosureId;

        /** 三份法律文档的不可变文件证据，不包含正文。 */
        java.util.List<FileEvidence> documents;

        /** 按参与方去重的已批准资料证据。 */
        java.util.List<PartyEvidence> parties;
    }

    /** 相关业务根修订。 */
    @Value
    @Accessors(fluent = true)
    public static class Revisions {
        /** Client 业务根修订。 */
        String clientChangeVersion;

        /** OE 业务根修订。 */
        String entityChangeVersion;

        /** Client 合同根修订。 */
        String clientAgreementChangeVersion;

        /** Partner 合同根修订；非 PARTNER 来源时为 null。 */
        String partnerAgreementChangeVersion;
    }
}
