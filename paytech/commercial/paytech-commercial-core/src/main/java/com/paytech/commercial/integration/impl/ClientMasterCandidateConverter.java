package com.paytech.commercial.integration.impl;

import java.util.*;

import com.paytech.client.master.api.checkout.response.CheckoutCandidateVerificationResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.commercial.domain.ClientMasterCandidates;
import com.paytech.commercial.domain.ClientMasterCandidates.*;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.PartyProfileFacts;

/**
 * client-master Wire 到 commercial 领域对象的一次性转换。
 * <li>格式非法或必需结构缺失时抛出 IllegalArgumentException，由调用方失败关闭；业务意义上的空值保留为 null。</li>
 */
final class ClientMasterCandidateConverter {
    /** 工具类不创建实例。 */
    private ClientMasterCandidateConverter() {
    }

    /** 候选响应转换。 */
    static ClientMasterCandidates candidates(CheckoutCandidatesResponseBody response) {
        var client = required(response.getClient());
        var kind = required(response.getClassification());
        var scope = required(response.getCommercialScope());
        List<Candidate> candidates = required(response.getCandidates()).stream()
            .map(ClientMasterCandidateConverter::candidate).toList();
        return new ClientMasterCandidates(new ClientFacts(id(client.getClientId()), id(client.getPartyId()), client.getStatus()),
            new Classification(id(kind.getBusinessProfileId()), id(kind.getClassificationId()), kind.getIndustryCode(),
                kind.getMccCode()),
            candidates,
            new CommercialCheckoutScope(id(client.getClientId()), required(scope.getEntityNos()),
                required(scope.getLocalPartnerIds()).stream().map(ClientMasterCandidateConverter::id).toList()),
            roots(required(response.getRootVersions())));
    }

    /** 复核响应：unchanged 为 true 时变化项必须为空。 */
    static boolean unchanged(CheckoutCandidateVerificationResponseBody response) {
        List<String> changes = required(response.getChangedRoots());
        if (response.isUnchanged() != changes.isEmpty()) {
            throw new IllegalArgumentException("verify 结果与变化项不一致");
        }
        return response.isUnchanged();
    }

    /** 资料响应：只允许请求范围内的资料，profileId 与 partyId 必填。 */
    static List<PartyProfileFacts> profiles(PartyProfilesResponseBody response, Set<Long> requested) {
        List<PartyProfileFacts> result = new ArrayList<>();
        for (var item : required(response.getItems())) {
            Long profileId = required(id(item.getProfileId()));
            if (!requested.contains(profileId)) {
                throw new IllegalArgumentException("返回了未请求的资料");
            }
            result.add(new PartyProfileFacts(profileId, required(id(item.getPartyId())),
                ClientMasterCheckoutClientImpl.time(item.getApprovedTime()), id(item.getDraftApplicationId()),
                item.getContentHash()));
        }
        return List.copyOf(result);
    }

    /** 单个候选转换。 */
    private static Candidate candidate(CheckoutCandidatesResponseBody.Candidate wire) {
        var source = required(wire.getSourceRelation());
        var partner = wire.getLocalPartner();
        return new Candidate(required(wire.getEntityNo()), id(wire.getOePartyId()),
            required(AcquiringQualification.fromCode(wire.getAcquiringQualification())), id(wire.getAffiliationId()),
            wire.getPriorityNo(), id(wire.getScopeId()), id(source.getSourceRelationId()), source.getSourceType(),
            id(source.getSourcePartnerId()),
            partner == null ? null
                : new LocalPartner(id(partner.getLocalPartnerId()), id(partner.getPartyId()), partner.getStatus()),
            provider(wire.getOeProvider()),
            required(wire.getLegalDocuments()).stream().map(d -> new LegalDocument(id(d.getLegalDocumentId()),
                d.getDocumentType(), id(d.getContentFileId()), d.getContentHash(), d.getStatus(),
                ClientMasterCheckoutClientImpl.time(d.getEffectiveFrom()), ClientMasterCheckoutClientImpl.time(d.getEffectiveTo()),
                ClientMasterCheckoutClientImpl.time(d.getApprovedTime()),
                ClientMasterCheckoutClientImpl.time(d.getPublishedTime()))).toList(),
            required(wire.getSellerDisclosures()).stream().map(s -> new SellerDisclosure(id(s.getDisclosureId()),
                s.getStatus(), ClientMasterCheckoutClientImpl.time(s.getEffectiveFrom()),
                ClientMasterCheckoutClientImpl.time(s.getEffectiveTo()), ClientMasterCheckoutClientImpl.time(s.getApprovedTime()),
                ClientMasterCheckoutClientImpl.time(s.getPublishedTime()), s.getPublicLegalName(),
                s.getPublicRegistrationNumber(), s.getPublicTaxRegistrationNumber(), s.getPublicAddressText(),
                s.getPublicSupportEmail(), s.getPublicSupportPhone())).toList(),
            required(wire.getCandidateDigest()));
    }

    /** OE 提供方转换；CLIENT 来源为空，角色数组必填。 */
    private static OeProvider provider(CheckoutCandidatesResponseBody.OeProvider wire) {
        if (wire == null) {
            return null;
        }
        return new OeProvider(required(id(wire.getLocalPartnerId())), required(id(wire.getPartyId())),
            required(wire.getStatus()), List.copyOf(required(wire.getRoles())));
    }

    /** 根版本按 {@code TYPE:recordId} 建索引；重复根视为响应不合法。 */
    private static Map<String, RootVersion> roots(CheckoutCandidatesResponseBody.RootVersions wire) {
        Map<String, RootVersion> result = new LinkedHashMap<>();
        if (wire.getClient() != null) {
            put(result, "CLIENT", wire.getClient());
        }
        putAll(result, "OPERATING_ENTITY", wire.getOperatingEntities());
        putAll(result, "LOCAL_PARTNER", wire.getLocalPartners());
        putAll(result, "CONSUMER_LEGAL_DOCUMENT", wire.getConsumerLegalDocuments());
        putAll(result, "SELLER_DISCLOSURE", wire.getSellerDisclosures());
        return Collections.unmodifiableMap(result);
    }

    /** 加入一组根。 */
    private static void putAll(Map<String, RootVersion> result, String type,
        List<CheckoutCandidatesResponseBody.RootVersion> values) {
        for (var value : required(values)) {
            put(result, type, value);
        }
    }

    /** 加入一个根。 */
    private static void put(Map<String, RootVersion> result, String type, CheckoutCandidatesResponseBody.RootVersion value) {
        String recordId = required(value.getRecordId());
        var root = new RootVersion(type, recordId, required(id(value.getHistoryId())), value.getVersion());
        if (result.put(type + ":" + recordId, root) != null) {
            throw new IllegalArgumentException("重复的根版本");
        }
    }

    /** 十进制 ID；空值保持 null，非法格式抛出。 */
    private static Long id(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("-?[0-9]{1,19}")) {
            throw new IllegalArgumentException("ID 格式非法");
        }
        return Long.parseLong(value);
    }

    /** 必需结构。 */
    private static <T> T required(T value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少必需字段");
        }
        return value;
    }
}
