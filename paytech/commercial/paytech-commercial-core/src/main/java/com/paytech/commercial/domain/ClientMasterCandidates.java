package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.client.master.api.enums.AcquiringQualification;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * client-master 候选接口返回的主档事实（design/06 §2.1），已由出站适配器完成格式解析。
 * <li>ID 与时间为空时保持 null，由解析规则在候选胜出时按原 require 语义报冲突；格式非法在出站适配器失败关闭。</li>
 * <li>首次结果在复核阶段原样复用：主档事实是否变化由 client-master verify 判断，commercial 只重读本库事实。</li>
 */
@Value
@Accessors(fluent = true)
public class ClientMasterCandidates {
    /** Client 主档。 */
    ClientFacts client;
    /** 唯一 Primary 经营分类。 */
    Classification classification;
    /** 按 Affiliation 优先级升序的候选。 */
    List<Candidate> candidates;
    /** 商务查询范围。 */
    CommercialCheckoutScope scope;
    /** 根版本：键为 {@code RECORD_TYPE:recordId}。 */
    Map<String, RootVersion> roots;

    /**
     * 读取指定根的当前修订。
     *
     * @param type 根类型 Code
     * @param recordId 业务记录 ID
     * @return 修订；首次快照不存在该根时为 null
     */
    public RootVersion root(String type, String recordId) {
        return roots.get(type + ":" + recordId);
    }

    /** Client 主档。 */
    @Value
    @Accessors(fluent = true)
    public static class ClientFacts {
        /** Client ID。 */
        Long clientId;
        /** 法律主体 Party ID。 */
        Long partyId;
        /** 状态 Code。 */
        String status;
    }

    /** 经营分类。 */
    @Value
    @Accessors(fluent = true)
    public static class Classification {
        /** 经营资料 ID。 */
        Long businessProfileId;
        /** Primary 分类 ID。 */
        Long classificationId;
        /** 行业代码。 */
        String industryCode;
        /** 四位 MCC。 */
        String mccCode;
    }

    /** 一个主档候选。 */
    @Value
    @Accessors(fluent = true)
    public static class Candidate {
        /** OE 编号。 */
        String entityNo;
        /** OE 法律主体 Party ID。 */
        Long oePartyId;
        /** OE 收单资质，决定能否承接 Client 及可承接的业务模式。 */
        AcquiringQualification acquiringQualification;
        /** Client/OE 关联 ID。 */
        Long affiliationId;
        /** 关联优先级。 */
        int priorityNo;
        /** 准确 Scope ID。 */
        Long scopeId;
        /** 来源关系 ID。 */
        Long sourceRelationId;
        /** 来源类型 Code。 */
        String sourceType;
        /** PARTNER 来源的 Local Partner ID。 */
        Long sourcePartnerId;
        /** PARTNER 来源的 Local Partner 主档；其他来源或主档缺失时为空。 */
        LocalPartner localPartner;
        /** OE 提供方：PARTNER 来源关系上的 Local Partner 及其 ACTIVE 角色；CLIENT 来源或主档缺失时为空。 */
        OeProvider oeProvider;
        /** 请求国家与 Locale 下该 OE 的全部法律文件。 */
        List<LegalDocument> legalDocuments;
        /** 请求国家与 Locale 下该 OE 的全部卖方披露。 */
        List<SellerDisclosure> sellerDisclosures;
        /** 主档事实摘要，复核时原样回传。 */
        String candidateDigest;
    }

    /** Local Partner 主档。 */
    @Value
    @Accessors(fluent = true)
    public static class LocalPartner {
        /** Local Partner ID。 */
        Long localPartnerId;
        /** 法律主体 Party ID。 */
        Long partyId;
        /** 状态 Code。 */
        String status;
    }

    /** OE 提供方。 */
    @Value
    @Accessors(fluent = true)
    public static class OeProvider {
        /** Local Partner ID。 */
        Long localPartnerId;
        /** 法律主体 Party ID。 */
        Long partyId;
        /** 状态 Code。 */
        String status;
        /** 当前 ACTIVE 角色 Code，升序。 */
        List<String> roles;
    }

    /** 消费者法律文件。 */
    @Value
    @Accessors(fluent = true)
    public static class LegalDocument {
        /** 文件 ID。 */
        Long legalDocumentId;
        /** 文件类型 Code。 */
        String documentType;
        /** 内容文件 ID。 */
        Long contentFileId;
        /** 内容 Hash。 */
        String contentHash;
        /** 状态 Code。 */
        String status;
        /** 生效开始时间。 */
        Instant effectiveFrom;
        /** 生效结束时间。 */
        Instant effectiveTo;
        /** 批准时间。 */
        Instant approvedTime;
        /** 发布时间。 */
        Instant publishedTime;
    }

    /** 卖方披露。 */
    @Value
    @Accessors(fluent = true)
    public static class SellerDisclosure {
        /** 披露 ID。 */
        Long disclosureId;
        /** 状态 Code。 */
        String status;
        /** 生效开始时间。 */
        Instant effectiveFrom;
        /** 生效结束时间。 */
        Instant effectiveTo;
        /** 批准时间。 */
        Instant approvedTime;
        /** 发布时间。 */
        Instant publishedTime;
        /** 公开的卖方法定名称。 */
        String publicLegalName;
        /** 公开的卖方注册编号。 */
        String publicRegistrationNumber;
        /** 公开的卖方税务登记编号；不披露时为空。 */
        String publicTaxRegistrationNumber;
        /** 公开的卖方地址文本。 */
        String publicAddressText;
        /** 公开的卖方支持邮箱。 */
        String publicSupportEmail;
        /** 公开的卖方支持电话；不披露时为空。 */
        String publicSupportPhone;
    }

    /** 根修订。 */
    @Value
    @Accessors(fluent = true)
    public static class RootVersion {
        /** 根类型 Code。 */
        String type;
        /** 业务记录 ID。 */
        String recordId;
        /** 根历史 ID。 */
        Long historyId;
        /** 当前修订。 */
        long version;
    }
}
