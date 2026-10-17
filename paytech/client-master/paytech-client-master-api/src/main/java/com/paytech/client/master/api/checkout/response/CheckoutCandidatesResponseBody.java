package com.paytech.client.master.api.checkout.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 只依赖客户主档事实即通过的 OE 候选及复核所需根版本。
 * <li>候选按 Affiliation 优先级升序；商务合同、定价与法律文件的落选判断由 commercial 完成。</li>
 * <li>法律文件与卖方披露返回该 OE 在请求国家与 Locale 下的全部原始记录，适用性由 commercial 按原规则判断。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutCandidatesResponseBody {
    /** Client 主档摘要。 */
    private ClientFacts client;

    /** 唯一 Primary 经营分类。 */
    private Classification classification;

    /** 按 Affiliation 优先级升序的候选；没有候选时为空数组。 */
    private List<Candidate> candidates;

    /** commercial 读取合同与条款的查询范围，覆盖该 Client 全部 Affiliation，而不只是候选。 */
    private CommercialScope commercialScope;

    /** 本次快照中与候选和优先级相关的业务根修订。 */
    private RootVersions rootVersions;

    /** Client 主档摘要。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientFacts {
        /** Client ID。 */
        private String clientId;

        /** Client 状态 Code。 */
        private String status;

        /** Client 法律主体 Party ID。 */
        private String partyId;

        /** Client 主体资料 ID。 */
        private String partyProfileId;

        /** Client 主体资料批准时间，UTC RFC 3339，最多六位小数。 */
        private String approvedAt;

        /** Client 级已存卡开关。 */
        private StoredCardSetting storedCardSetting;
    }

    /** Client 级已存卡开关。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoredCardSetting {
        /** 是否开通存卡与已存卡支付；没有记录视为未开通。 */
        private boolean enabled;
    }

    /** 经营分类。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Classification {
        /** Client 经营资料 ID。 */
        private String businessProfileId;

        /** Primary 分类 ID。 */
        private String classificationId;

        /** Primary 行业代码。 */
        private String industryCode;

        /** Primary 四位 MCC。 */
        private String mccCode;
    }

    /** 一个主档候选。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        /** OE 不可变五位编号。 */
        private String entityNo;

        /** OE 法律主体 Party ID。 */
        private String oePartyId;

        /** Client/OE 关联 ID。 */
        private String affiliationId;

        /** 关联优先级；正整数，越小越优先。 */
        private int priorityNo;

        /** 批准的准确 Scope ID。 */
        private String scopeId;

        /** 唯一有效来源关系。 */
        private SourceRelation sourceRelation;

        /** PARTNER 来源的 Local Partner；其他来源或主档缺失时为 null。 */
        private LocalPartner localPartner;

        /** 请求国家与 Locale 下该 OE 的全部消费者法律文件原始记录。 */
        private List<LegalDocument> legalDocuments;

        /** 请求国家与 Locale 下该 OE 的全部卖方披露原始记录。 */
        private List<SellerDisclosure> sellerDisclosures;

        /** 候选使用的全部主档事实的摘要（SHA-256 小写十六进制），复核时用于判断事实未变。 */
        private String candidateDigest;
    }

    /** 来源关系。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRelation {
        /** 来源关系 ID。 */
        private String sourceRelationId;

        /** 来源类型 Code：PLATFORM、CLIENT 或 PARTNER。 */
        private String sourceType;

        /** CLIENT 来源的 Client ID，其他来源为 null。 */
        private String sourceClientId;

        /** PARTNER 来源的 Local Partner ID，其他来源为 null。 */
        private String sourcePartnerId;
    }

    /** Local Partner 主档。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalPartner {
        /** Local Partner ID。 */
        private String localPartnerId;

        /** Local Partner 法律主体 Party ID。 */
        private String partyId;

        /** Local Partner 状态 Code。 */
        private String status;
    }

    /** 消费者法律文件原始记录。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalDocument {
        /** 法律文件 ID。 */
        private String legalDocumentId;

        /** 文件类型 Code：CONSUMER_TERMS、REFUND_POLICY 或 PRIVACY_NOTICE。 */
        private String documentType;

        /** 不可变内容文件 ID；可能为空，由 commercial 判断。 */
        private String contentFileId;

        /** 内容 SHA-256；可能为空或非法，由 commercial 判断。 */
        private String contentHash;

        /** 文件状态 Code。 */
        private String status;

        /** 生效开始时间，UTC RFC 3339。 */
        private String effectiveFrom;

        /** 生效结束时间，UTC RFC 3339；不限期为 null。 */
        private String effectiveTo;

        /** 批准时间；未批准为 null。 */
        private String approvedTime;

        /** 发布时间；未发布为 null。 */
        private String publishedTime;
    }

    /** 卖方披露原始记录。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerDisclosure {
        /** 卖方披露 ID。 */
        private String disclosureId;

        /** 披露状态 Code。 */
        private String status;

        /** 生效开始时间，UTC RFC 3339。 */
        private String effectiveFrom;

        /** 生效结束时间；不限期为 null。 */
        private String effectiveTo;

        /** 批准时间；未批准为 null。 */
        private String approvedTime;

        /** 发布时间；未发布为 null。 */
        private String publishedTime;
    }

    /** commercial 的查询范围。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommercialScope {
        /** 该 Client 全部 Affiliation 的 OE 编号，升序。 */
        private List<String> entityNos;

        /** 上述 OE 的全部 PARTNER 来源 Local Partner ID，升序去重。 */
        private List<String> localPartnerIds;
    }

    /** 业务根修订。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootVersions {
        /** Client 根；不存在时为 null。 */
        private RootVersion client;

        /** 全部关联 OE 的根，按 recordId 升序。 */
        private List<RootVersion> operatingEntities;

        /** 全部 PARTNER 来源 Local Partner 的根，按 recordId 升序。 */
        private List<RootVersion> localPartners;

        /** 关联 OE 的消费者法律文件根，按 recordId 升序。 */
        private List<RootVersion> consumerLegalDocuments;

        /** 关联 OE 的卖方披露根，按 recordId 升序。 */
        private List<RootVersion> sellerDisclosures;
    }

    /** 一个业务根的当前修订。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootVersion {
        /** 业务记录 ID。 */
        private String recordId;

        /** 根历史 ID。 */
        private String historyId;

        /** 当前修订号，正整数。 */
        private long version;
    }
}
