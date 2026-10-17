package com.paytech.commercial.domain;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.*;
import com.paytech.commercial.domain.CheckoutCandidateContext.EntityEligibility;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 一次解析的原始本地事实；只做候选构造和一致性判断，不持有服务、事务或执行回调。 */
@Getter
@Accessors(fluent = true)
public final class CheckoutResolutionContext {
    /** 本次查询的原始业务条件与冻结时点。 */
    private final CheckoutQuery query;
    /** 已按请求身份取得的 Client 原对象。 */
    private final Client client;
    /** 同一读事务取得的经营资料及分类确认原对象。 */
    private final List<ClientBusinessProfile> businessProfiles;
    /** 同一读事务取得的经营分类原对象。 */
    private final List<ClientBusinessClassification> classifications;
    /** 同一读事务取得的Client/OE 授权关联原对象。 */
    private final List<ClientEntityAffiliation> affiliations;
    /** 同一读事务取得的经营主体原对象。 */
    private final List<OperatingEntity> entities;
    /** 同一读事务取得的来源关系原对象。 */
    private final List<EntitySourceRelation> sources;
    /** 同一读事务取得的准确准入范围原对象。 */
    private final List<OperatingEntityScope> scopes;
    /** 同一读事务取得的范围审批申请原对象。 */
    private final List<OperatingEntityApplication> scopeApplications;
    /** 同一读事务取得的来源合作方原对象。 */
    private final List<LocalPartner> partners;
    /** 同一读事务取得的适用合同原对象。 */
    private final List<Agreement> agreements;
    /** 同一读事务取得的Partner 合同覆盖范围原对象。 */
    private final List<AgreementOperatingEntity> agreementEntities;
    /** 同一读事务取得的合同参与方和共签证据原对象。 */
    private final List<AgreementParty> agreementParties;
    /** 同一读事务取得的合同签署文件原对象。 */
    private final List<AgreementDocumentRef> agreementDocuments;
    /** 同一读事务取得的已批准的参与方资料原对象。 */
    private final List<PartyProfile> partyProfiles;
    /** 同一读事务取得的条款计划原对象。 */
    private final List<CommercialTermPlan> termPlans;
    /** 同一读事务取得的不可变条款版本原对象。 */
    private final List<CommercialTermVersion> termVersions;
    /** 同一读事务取得的准确 OE 定价原对象。 */
    private final List<CommercialTermPrice> termPrices;
    /** 同一读事务取得的定价组件原对象。 */
    private final List<CommercialTermComponent> termComponents;
    /** 同一读事务取得的定价阶梯原对象。 */
    private final List<CommercialTermComponentBand> termBands;
    /** 同一读事务取得的支付方式 Profile原对象。 */
    private final List<PaymentMethodProfile> methodProfiles;
    /** 同一读事务取得的消费者法律文件原对象。 */
    private final List<ConsumerLegalDocument> legalDocuments;
    /** 同一读事务取得的卖方披露原对象。 */
    private final List<SellerDisclosure> sellerDisclosures;
    /** 同一读事务取得的影响候选与优先级的业务根修订原对象。 */
    private final List<BusinessChangeRecord> roots;

    /** 一次接收同一事务取得的全部原始事实，空列表仅表示对应查询没有返回记录。 */
    @Builder
    private CheckoutResolutionContext(CheckoutQuery query, Client client, List<ClientBusinessProfile> businessProfiles,
        List<ClientBusinessClassification> classifications, List<ClientEntityAffiliation> affiliations,
        List<OperatingEntity> entities, List<EntitySourceRelation> sources, List<OperatingEntityScope> scopes,
        List<OperatingEntityApplication> scopeApplications, List<LocalPartner> partners, List<Agreement> agreements,
        List<AgreementOperatingEntity> agreementEntities, List<AgreementParty> agreementParties,
        List<AgreementDocumentRef> agreementDocuments, List<PartyProfile> partyProfiles,
        List<CommercialTermPlan> termPlans, List<CommercialTermVersion> termVersions,
        List<CommercialTermPrice> termPrices, List<CommercialTermComponent> termComponents,
        List<CommercialTermComponentBand> termBands, List<PaymentMethodProfile> methodProfiles,
        List<ConsumerLegalDocument> legalDocuments, List<SellerDisclosure> sellerDisclosures,
        List<BusinessChangeRecord> roots) {
        this.query = Objects.requireNonNull(query);
        if (client == null || client.clientId() != query.clientId()) {
            throw new FailException(CLIENT_NOT_FOUND);
        }
        this.client = client;
        this.businessProfiles = List.copyOf(businessProfiles);
        this.classifications = List.copyOf(classifications);
        this.affiliations = List.copyOf(affiliations);
        this.entities = List.copyOf(entities);
        this.sources = List.copyOf(sources);
        this.scopes = List.copyOf(scopes);
        this.scopeApplications = List.copyOf(scopeApplications);
        this.partners = List.copyOf(partners);
        this.agreements = List.copyOf(agreements);
        this.agreementEntities = List.copyOf(agreementEntities);
        this.agreementParties = List.copyOf(agreementParties);
        this.agreementDocuments = List.copyOf(agreementDocuments);
        this.partyProfiles = List.copyOf(partyProfiles);
        this.termPlans = List.copyOf(termPlans);
        this.termVersions = List.copyOf(termVersions);
        this.termPrices = List.copyOf(termPrices);
        this.termComponents = List.copyOf(termComponents);
        this.termBands = List.copyOf(termBands);
        this.methodProfiles = List.copyOf(methodProfiles);
        this.legalDocuments = List.copyOf(legalDocuments);
        this.sellerDisclosures = List.copyOf(sellerDisclosures);
        this.roots = List.copyOf(roots);
    }

    /** 按授权优先级惰性构造完整候选，低优先级合同不会提前影响已胜出者。 */
    public Iterable<CheckoutCandidateContext> candidates() {
        if (!code(client.status(), "ACTIVE")) {
            throw new FailException(CLIENT_NOT_ELIGIBLE);
        }
        ClientBusinessClassification classification = classification(query.asOf());
        if (classification == null) {
            throw new FailException(CLIENT_NOT_ELIGIBLE);
        }
        Set<BusinessChangeRecord> roots = revisions();
        List<EntityEligibility> entities = eligibleEntities(classification, query.asOf());
        return () -> entities.stream().map(entity -> buildCandidate(classification, entity, roots))
            .filter(Objects::nonNull).iterator();
    }

    /** 外部资格检查后的新事实必须保留原查询、原选择和所有候选相关 Root。 */
    public void verifyUnchanged(CheckoutResolutionContext current, CheckoutCandidateContext selected, Instant now) {
        if (!query.equals(current.query) || !query.equals(selected.query()) || !revisions().equals(current.revisions())
            || !current.stillAvailable(selected, now)) {
            throw new FailException(CCT_CONTEXT_CHANGED);
        }
    }

    /** 当前状态和原准确引用都需有效；不能用另一 OE 或同 OE 的后继授权代替。 */
    private boolean stillAvailable(CheckoutCandidateContext selected, Instant now) {
        if (!code(client.status(), "ACTIVE")) {
            return false;
        }
        ClientBusinessClassification current = classification(now);
        if (current == null || eligibleEntities(current, now).stream()
            .noneMatch(entity -> selected.eligibility().sameAuthorization(entity))) {
            return false;
        }
        if (agreements.stream().filter(value -> selected.referencesAgreement(value.agreementId()))
            .anyMatch(value -> code(value.status(), "TERMINATED") || code(value.status(), "CANCELLED"))) {
            return false;
        }
        for (CheckoutCandidateContext candidate : candidates()) {
            if (candidate.entityNo().equals(selected.entityNo())) {
                return candidate.equals(selected);
            }
        }
        return false;
    }

    /** 业务根使用原记录的集合比较，顺序不影响复核，重复或无效根明确报错。 */
    private Set<BusinessChangeRecord> revisions() {
        Set<String> identities = new HashSet<>();
        for (BusinessChangeRecord root : this.roots) {
            require(root.recordType() != null && root.aggregateRootType() != null
                && root.recordType().getCode().equals(root.aggregateRootType().getCode())
                && Objects.equals(root.recordId(), root.aggregateRootId()) && root.currentVersion() != null
                && root.currentVersion() > 0 && identities.add(root.recordType().getCode() + ":" + root.recordId()));
        }
        return Set.copyOf(this.roots);
    }

    private ClientBusinessClassification classification(Instant at) {
        ClientBusinessProfile profile = unique(businessProfiles, p -> Objects.equals(p.clientId(), query.clientId()));
        if (profile == null || !validHash(profile.contentHash()) || profile.classificationCaseId() == null
            || profile.classificationConfirmedBy() == null || profile.classificationConfirmedTime() == null
            || profile.classificationConfirmedTime().isAfter(at)) {
            return null;
        }
        PartyProfile party = unique(partyProfiles, p -> Objects.equals(p.partyId(), client.partyId()));
        if (party == null || party.approvedTime() == null || party.approvedTime().isAfter(at)
            || party.draftApplicationId() != null || !validHash(party.contentHash())) {
            return null;
        }
        ClientBusinessClassification result = unique(classifications,
            c -> c.businessProfileId().equals(profile.businessProfileId()) && Objects.equals(c.primarySlot(), 1));
        if (result != null) {
            require(result.industryCode() != null && !result.industryCode().isBlank() && result.mccCode() != null
                && result.mccCode().matches("[0-9]{4}"));
        }
        return result;
    }

    private List<EntityEligibility> eligibleEntities(ClientBusinessClassification classification, Instant at) {
        List<ClientEntityAffiliation> activeAffiliations = affiliations.stream()
            .filter(a -> code(a.status(), "ACTIVE") && applicable(a.effectiveFrom(), a.effectiveTo(), at))
            .sorted(Comparator.comparing(ClientEntityAffiliation::priorityNo)).toList();
        Set<Integer> priorities = new HashSet<>();
        Set<String> uniqueEntities = new HashSet<>();
        List<EntityEligibility> result = new ArrayList<>();
        for (ClientEntityAffiliation affiliation : activeAffiliations) {
            require(affiliation.priorityNo() != null && affiliation.priorityNo() > 0
                && Objects.equals(affiliation.activeSlot(), 1) && priorities.add(affiliation.priorityNo())
                && uniqueEntities.add(affiliation.entityNo()));
            EntityEligibility candidate = eligibleEntity(classification, affiliation, at);
            if (candidate != null) {
                result.add(candidate);
            }
        }
        return result;
    }

    private EntityEligibility eligibleEntity(ClientBusinessClassification classification,
        ClientEntityAffiliation affiliation, Instant at) {
        OperatingEntity entity = unique(entities, e -> e.entityNo().equals(affiliation.entityNo()));
        require(entity != null && entity.entityNo().matches("[A-Z]{2}(?!000)[0-9]{3}"));
        if (!code(entity.status(), "ACTIVE")) {
            return null;
        }
        EntitySourceRelation source = unique(sources, s -> s.entityNo().equals(entity.entityNo())
            && code(s.status(), "ACTIVE") && applicable(s.effectiveFrom(), s.effectiveTo(), at));
        if (source == null) {
            return null;
        }
        validateSource(source);
        OperatingEntityScope scope = unique(scopes,
            s -> s.entityNo().equals(entity.entityNo()) && code(s.status(), "ACTIVE")
                && applicable(s.effectiveFrom(), s.effectiveTo(), at) && s.consumerCountryCode().equals(query.country())
                && s.currency().equals(query.currency()) && s.industryCode().equals(classification.industryCode())
                && s.mccCode().equals(classification.mccCode()));
        if (scope == null) {
            return null;
        }
        require(Objects.equals(scope.activeSlot(), 1));
        OperatingEntityApplication application =
            unique(scopeApplications, a -> a.applicationId().equals(scope.applicationId()));
        require(application != null && application.entityNo().equals(entity.entityNo())
            && code(application.status(), "APPROVED")
            && Objects.equals(application.approvedSourceRelationId(), source.sourceRelationId()));
        return new EntityEligibility(affiliation, entity, source, scope);
    }

    private void validateSource(EntitySourceRelation source) {
        require(Objects.equals(source.activeSourceSlot(), 1));
        if (code(source.sourceType(), "PLATFORM")) {
            require(source.sourceClientId() == null && source.sourcePartnerId() == null
                && source.clientSourceBasis() == null);
        } else if (code(source.sourceType(), "CLIENT")) {
            require(source.sourceClientId() != null && source.sourcePartnerId() == null
                && source.clientSourceBasis() != null);
        } else {
            require(code(source.sourceType(), "PARTNER") && source.sourcePartnerId() != null
                && source.sourceClientId() == null && source.clientSourceBasis() == null);
        }
    }

    /** 完整本地资格通过后一次构造候选上下文，不在后续步骤逐层补字段。 */
    private CheckoutCandidateContext buildCandidate(ClientBusinessClassification classification,
        EntityEligibility entity, Set<BusinessChangeRecord> roots) {
        Agreement clientAgreement = unique(agreements,
            value -> code(value.agreementType(), "CLIENT") && Objects.equals(value.clientId(), client.clientId())
                && Objects.equals(value.entityNo(), entity.entity().entityNo()) && agreementApplicable(value));
        if (clientAgreement == null) {
            return null;
        }
        require(clientAgreement.localPartnerId() == null);
        requireSigned(clientAgreement, client.partyId(), "CLIENT", entity.entity().partyId());
        CheckoutPricingContext clientTerms =
            CheckoutPricingContext.resolve(this, clientAgreement, entity, "CLIENT_PRICING");
        if (clientTerms == null) {
            return null;
        }
        CheckoutCandidateContext.PartnerTerms partner = partner(entity);
        if (code(entity.source().sourceType(), "PARTNER") && partner == null) {
            return null;
        }
        CheckoutCandidateContext.Legal legal = legal(entity, clientAgreement);
        if (legal == null) {
            return null;
        }
        CheckoutCandidateContext result = new CheckoutCandidateContext(query, client, classification, entity,
            clientAgreement, clientTerms, partner, legal, root(roots, "CLIENT", id(client.clientId())),
            root(roots, "OPERATING_ENTITY", entity.entity().entityNo()),
            root(roots, "AGREEMENT", id(clientAgreement.agreementId())),
            partner == null ? null : root(roots, "AGREEMENT", id(partner.agreement().agreementId())));
        result.validateEvidence();
        return result;
    }

    private CheckoutCandidateContext.PartnerTerms partner(EntityEligibility candidate) {
        if (!code(candidate.source().sourceType(), "PARTNER")) {
            return null;
        }
        long partnerId = candidate.source().sourcePartnerId();
        LocalPartner partner = unique(this.partners, p -> p.localPartnerId() == partnerId);
        require(partner != null);
        if (!code(partner.status(), "ACTIVE")) {
            return null;
        }
        Set<Long> covered =
            this.agreementEntities.stream().filter(a -> a.entityNo().equals(candidate.entity().entityNo()))
                .map(AgreementOperatingEntity::agreementId).collect(Collectors.toSet());
        Agreement agreement =
            unique(agreements, a -> code(a.agreementType(), "PARTNER") && Objects.equals(a.localPartnerId(), partnerId)
                && covered.contains(a.agreementId()) && agreementApplicable(a));
        if (agreement == null) {
            return null;
        }
        require(agreement.clientId() == null && agreement.entityNo() == null);
        requireSigned(agreement, partner.partyId(), "LOCAL_PARTNER", candidate.entity().partyId());
        CheckoutPricingContext share = CheckoutPricingContext.resolve(this, agreement, candidate, "PARTNER_SHARE");
        CheckoutPricingContext charge =
            CheckoutPricingContext.resolve(this, agreement, candidate, "PLATFORM_SERVICE_CHARGE");
        if (share == null || charge == null) {
            return null;
        }
        AgreementParty signatory = unique(this.agreementParties,
            p -> p.agreementId().equals(agreement.agreementId()) && code(p.partyRole(), "PLATFORM_SIGNATORY"));
        require(signatory != null);
        AgreementOperatingEntity association = unique(this.agreementEntities,
            a -> a.agreementId().equals(agreement.agreementId()) && a.entityNo().equals(candidate.entity().entityNo()));
        return new CheckoutCandidateContext.PartnerTerms(partner, agreement, association, share, charge, signatory);
    }

    private void requireSigned(Agreement agreement, Long ownerParty, String ownerRole, Long entityParty) {
        List<AgreementParty> parties =
            agreementParties.stream().filter(p -> p.agreementId().equals(agreement.agreementId())).toList();
        require(!parties.isEmpty());
        require(parties.stream().anyMatch(p -> code(p.partyRole(), ownerRole) && p.partyId().equals(ownerParty)));
        require(
            parties.stream().filter(p -> code(p.partyRole(), ownerRole)).allMatch(p -> p.partyId().equals(ownerParty)));
        List<AgreementDocumentRef> documents =
            agreementDocuments.stream().filter(d -> d.agreementId().equals(agreement.agreementId())).toList();
        require(documents.stream().filter(d -> code(d.documentType(), "SIGNING_DOCUMENT")).count() == 1);
        require(documents.stream().filter(d -> code(d.documentType(), "EXECUTED_DOCUMENT")).count() <= 1);
        require(
            parties.stream().anyMatch(p -> code(p.partyRole(), "OPERATING_ENTITY") && p.partyId().equals(entityParty)));
        Map<Long, List<AgreementParty>> groups =
            parties.stream().collect(Collectors.groupingBy(AgreementParty::partyId));
        for (List<AgreementParty> group : groups.values()) {
            validateSignature(agreement, group);
        }
    }

    private void validateSignature(Agreement agreement, List<AgreementParty> group) {
        AgreementParty first = group.getFirst();
        require(first.acceptedTime() != null && first.acceptanceMethod() != null && first.acceptanceReference() != null
            && first.acceptanceRecordedBy() != null && first.acceptanceRecordedAt() != null
            && !first.acceptedTime().isAfter(agreement.signedTime()));
        Set<String> roles = new HashSet<>();
        for (AgreementParty party : group) {
            require(roles.add(party.partyRole().getCode()) && sameSignature(first, party));
            PartyProfile profile = unique(partyProfiles, p -> p.profileId().equals(party.partyProfileId()));
            require(profile != null && profile.partyId().equals(party.partyId()) && profile.approvedTime() != null
                && !profile.approvedTime().isAfter(party.acceptedTime()) && profile.draftApplicationId() == null
                && validHash(profile.contentHash()));
        }
        require(group.stream().filter(p -> Objects.equals(p.acceptanceRefSlot(), 1)).count() == 1);
        Long firstId = group.stream().map(AgreementParty::agreementPartyId).min(Long::compare).orElseThrow();
        require(group.stream().allMatch(p -> p.agreementPartyId().equals(firstId)
            ? Objects.equals(p.acceptanceRefSlot(), 1) : p.acceptanceRefSlot() == null));
        AgreementDocumentRef document =
            unique(agreementDocuments, d -> d.agreementDocumentId().equals(first.evidenceDocumentId()));
        require(document != null && document.agreementId().equals(agreement.agreementId())
            && validHash(document.contentHash()));
        if (code(first.acceptanceMethod(), "CLICK_ACCEPTANCE")) {
            require(first.acceptedByActorId() != null && code(document.documentType(), "SIGNING_DOCUMENT"));
        } else {
            require(code(document.documentType(), "EXECUTED_DOCUMENT"));
        }
    }

    /** 同一 Party 的多个合同角色必须指向同一份真实签署事实。 */
    private boolean sameSignature(AgreementParty first, AgreementParty other) {
        return Objects.equals(first.partyProfileId(), other.partyProfileId())
            && Objects.equals(first.acceptanceMethod(), other.acceptanceMethod())
            && Objects.equals(first.acceptedByActorId(), other.acceptedByActorId())
            && Objects.equals(first.acceptedTime(), other.acceptedTime())
            && Objects.equals(first.evidenceDocumentId(), other.evidenceDocumentId())
            && Objects.equals(first.acceptanceReference(), other.acceptanceReference())
            && Objects.equals(first.acceptanceRecordedBy(), other.acceptanceRecordedBy())
            && Objects.equals(first.acceptanceRecordedAt(), other.acceptanceRecordedAt());
    }

    private CheckoutCandidateContext.Legal legal(EntityEligibility candidate, Agreement agreement) {
        List<ConsumerLegalDocument> documents = new ArrayList<>();
        for (String type : List.of("CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE")) {
            ConsumerLegalDocument document = unique(legalDocuments,
                d -> code(d.documentType(), type) && d.entityNo().equals(candidate.entity().entityNo())
                    && d.countryCode().equals(query.country()) && d.locale().equals(query.locale()) && legalApplicable(
                        d.status(), d.effectiveFrom(), d.effectiveTo(), d.approvedTime(), d.publishedTime()));
            if (document == null) {
                return null;
            }
            require(validHash(document.contentHash()) && document.contentFileId() != null);
            documents.add(document);
        }
        SellerDisclosure seller = unique(sellerDisclosures, s -> s.entityNo().equals(candidate.entity().entityNo())
            && s.countryCode().equals(query.country()) && s.locale().equals(query.locale())
            && legalApplicable(s.status(), s.effectiveFrom(), s.effectiveTo(), s.approvedTime(), s.publishedTime()));
        if (seller == null) {
            return null;
        }
        List<PartyProfile> parties =
            agreementParties.stream().filter(party -> party.agreementId().equals(agreement.agreementId()))
                .map(party -> unique(partyProfiles, profile -> profile.profileId().equals(party.partyProfileId())))
                .distinct().sorted(Comparator.comparing(PartyProfile::profileId)).toList();
        return new CheckoutCandidateContext.Legal(documents, seller, parties);
    }

    private boolean legalApplicable(CodeEnum status, Instant from, Instant to, Instant approved, Instant published) {
        return (code(status, "ACTIVE") || code(status, "EXPIRED")) && approved != null && published != null
            && !approved.isAfter(query.asOf()) && !published.isAfter(query.asOf())
            && applicable(from, to, query.asOf());
    }

    private boolean agreementApplicable(Agreement agreement) {
        return !code(agreement.status(), "DRAFT") && !code(agreement.status(), "PENDING_SIGNATURE")
            && !code(agreement.status(), "CANCELLED") && agreement.signedTime() != null
            && agreement.activatedTime() != null && !agreement.signedTime().isAfter(query.asOf())
            && !agreement.activatedTime().isAfter(query.asOf())
            && applicable(agreement.effectiveFrom(), agreement.effectiveTo(), query.asOf());
    }

    /** 对外引用的根必须真实存在，不能以零版本补齐。 */
    private BusinessChangeRecord root(Set<BusinessChangeRecord> roots, String type, String identity) {
        BusinessChangeRecord result = unique(roots.stream().toList(),
            value -> code(value.recordType(), type) && identity.equals(value.recordId()));
        require(result != null);
        return result;
    }

    static boolean applicable(Instant from, Instant to, Instant time) {
        require(from != null && (to == null || from.isBefore(to)));
        return !from.isAfter(time) && (to == null || time.isBefore(to));
    }

    static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    static boolean code(CodeEnum value, String code) {
        return value != null && value.getCode().equals(code);
    }

    static String id(Long value) {
        require(value != null && value > 0);
        return value.toString();
    }

    static <T> T unique(List<T> values, Predicate<T> predicate) {
        List<T> matches = values.stream().filter(predicate).toList();
        require(matches.size() <= 1);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    static void require(boolean condition) {
        if (!condition) {
            throw new FailException(CCT_CONTEXT_AMBIGUOUS);
        }
    }

}
