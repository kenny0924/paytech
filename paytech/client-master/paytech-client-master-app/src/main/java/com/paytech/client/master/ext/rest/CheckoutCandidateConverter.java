package com.paytech.client.master.ext.rest;

import static com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.INVALID_CANDIDATE_QUERY;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody.*;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.domain.checkout.CandidateVerification;
import com.paytech.client.master.domain.checkout.CheckoutCandidateQuery;
import com.paytech.client.master.domain.checkout.CheckoutEligibility;
import com.paytech.client.master.domain.checkout.CheckoutEligibilityContext;
import com.paytech.client.master.domain.checkout.EntityCandidate;
import com.paytech.foundation.common.error.FailException;

/** 候选查询 Wire 与领域对象之间的一次性转换；不访问数据库或执行业务判断。 */
final class CheckoutCandidateConverter {
    private static final DateTimeFormatter INSTANT = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    /** 工具类不创建实例。 */
    private CheckoutCandidateConverter() {
    }

    /** 请求转换为已校验的候选查询。 */
    static CheckoutCandidateQuery query(CheckoutCandidatesRequestBody request) {
        return CheckoutCandidateQuery.resolve(request.getClientId(), request.getConsumerCountryCode(), request.getCurrency(),
            request.getLocale(), request.getContextAsOfTime());
    }

    /** 复核请求转换为领域输入；根版本转为与本服务快照相同的比较键。 */
    static CandidateVerification verification(VerifyCheckoutCandidateRequestBody request) {
        var selected = request.getCandidate();
        Set<String> roots = new TreeSet<>();
        RootVersions versions = request.getRootVersions();
        if (versions.getClient() != null) {
            roots.add(rootKey("CLIENT", versions.getClient()));
        }
        addRoots(roots, "OPERATING_ENTITY", versions.getOperatingEntities());
        addRoots(roots, "LOCAL_PARTNER", versions.getLocalPartners());
        addRoots(roots, "CONSUMER_LEGAL_DOCUMENT", versions.getConsumerLegalDocuments());
        addRoots(roots, "SELLER_DISCLOSURE", versions.getSellerDisclosures());
        var authorization = new CandidateVerification.SelectedAuthorization(selected.getEntityNo(),
            positiveId(selected.getAffiliationId()), selected.getPriorityNo(), positiveId(selected.getScopeId()),
            positiveId(selected.getSourceRelationId()));
        return new CandidateVerification(query(request.getQuery()), roots, authorization, selected.getCandidateDigest());
    }

    /** 候选领域结果转换为响应。 */
    static CheckoutCandidatesResponseBody response(CheckoutEligibility result) {
        var client = result.client();
        var profile = result.clientProfile();
        var classification = result.classification();
        var facts = new ClientFacts(id(client.clientId()), client.status().getCode(), id(client.partyId()),
            id(profile.profileId()), time(profile.approvedTime()), new StoredCardSetting(result.storedCardEnabled()));
        var kind = new Classification(id(classification.businessProfileId()), id(classification.classificationId()),
            classification.industryCode(), classification.mccCode());
        var candidates = result.candidates().stream().map(CheckoutCandidateConverter::candidate).toList();
        var scope = new CommercialScope(result.entityNos(),
            result.localPartnerIds().stream().map(Object::toString).toList());
        return new CheckoutCandidatesResponseBody(facts, kind, candidates, scope, roots(result.roots()));
    }

    /** 参与方资料转换为响应。 */
    static PartyProfilesResponseBody profiles(List<CheckoutRecords.PartyProfile> profiles) {
        return new PartyProfilesResponseBody(profiles.stream().map(p -> new PartyProfilesResponseBody.PartyProfile(
            id(p.profileId()), id(p.partyId()), time(p.approvedTime()), id(p.draftApplicationId()), p.contentHash())).toList());
    }

    /** 规范正十进制 BIGINT。 */
    static long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
    }

    /** 单个候选转换；法律文件与卖方披露保留原始字段，由 commercial 判断适用性。 */
    private static Candidate candidate(EntityCandidate candidate) {
        var eligibility = candidate.eligibility();
        var source = eligibility.source();
        var partner = candidate.partner();
        return new Candidate(candidate.entityNo(), id(eligibility.entity().partyId()),
            id(eligibility.affiliation().affiliationId()), eligibility.affiliation().priorityNo(),
            id(eligibility.scope().scopeId()),
            new SourceRelation(id(source.sourceRelationId()), source.sourceType().getCode(), id(source.sourceClientId()),
                id(source.sourcePartnerId())),
            partner == null ? null
                : new LocalPartner(id(partner.localPartnerId()), id(partner.partyId()), code(partner.status())),
            candidate.legalDocuments().stream().map(d -> new LegalDocument(id(d.legalDocumentId()),
                code(d.documentType()), id(d.contentFileId()), d.contentHash(), code(d.status()), time(d.effectiveFrom()),
                time(d.effectiveTo()), time(d.approvedTime()), time(d.publishedTime()))).toList(),
            candidate.sellerDisclosures().stream().map(s -> new SellerDisclosure(id(s.disclosureId()), code(s.status()),
                time(s.effectiveFrom()), time(s.effectiveTo()), time(s.approvedTime()), time(s.publishedTime()))).toList(),
            candidate.digest());
    }

    /** 根按类型分组输出。 */
    private static RootVersions roots(List<BusinessChangeRecord> roots) {
        RootVersion client = null;
        List<RootVersion> entities = new ArrayList<>();
        List<RootVersion> partners = new ArrayList<>();
        List<RootVersion> documents = new ArrayList<>();
        List<RootVersion> sellers = new ArrayList<>();
        for (BusinessChangeRecord root : roots) {
            var version = new RootVersion(root.recordId(), id(root.historyId()), root.currentVersion());
            switch (root.recordType().getCode()) {
                case "CLIENT" -> client = version;
                case "OPERATING_ENTITY" -> entities.add(version);
                case "LOCAL_PARTNER" -> partners.add(version);
                case "CONSUMER_LEGAL_DOCUMENT" -> documents.add(version);
                default -> sellers.add(version);
            }
        }
        return new RootVersions(client, entities, partners, documents, sellers);
    }

    /** 加入一组根比较键。 */
    private static void addRoots(Set<String> roots, String type, List<RootVersion> versions) {
        if (versions == null) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
        for (RootVersion version : versions) {
            roots.add(rootKey(type, version));
        }
    }

    /** 与快照一致的根比较键；historyId 必须为规范 ID。 */
    private static String rootKey(String type, RootVersion version) {
        if (version == null || version.getRecordId() == null) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
        return CheckoutEligibilityContext.rootKey(type, version.getRecordId(), positiveId(version.getHistoryId()),
            version.getVersion());
    }

    /** ID 输出为十进制字符串，空值保持 null。 */
    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** UTC 微秒精度时间，空值保持 null。 */
    private static String time(Instant value) {
        return value == null ? null : INSTANT.format(value);
    }

    /** 枚举 Code，空值保持 null。 */
    private static String code(com.paytech.foundation.common.enums.CodeEnum value) {
        return value == null ? null : value.getCode();
    }
}
