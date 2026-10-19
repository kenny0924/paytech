package com.paytech.commercial.domain;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.*;
import com.paytech.commercial.domain.ClientMasterCandidates.Candidate;
import com.paytech.commercial.domain.ClientMasterCandidates.LegalDocument;
import com.paytech.commercial.domain.ClientMasterCandidates.LocalPartner;
import com.paytech.commercial.domain.ClientMasterCandidates.RootVersion;
import com.paytech.commercial.domain.ClientMasterCandidates.SellerDisclosure;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 一次解析的原始事实：client-master 返回的有序主档候选、本库商务快照与参与方资料（design/06 §2.2）。
 * <li>只做候选构造和一致性判断，不持有服务、事务或执行回调。</li>
 * <li>逐候选的合同、定价、Partner 与法律证据校验及落选语义自原 CCT 单库实现原样保留；只依赖主档事实的校验已在 client-master
 * 完成。</li>
 */
@Getter
@Accessors(fluent = true)
public final class CheckoutResolutionContext {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutResolutionContext.class);

    /** 本次查询的原始业务条件与冻结时点。 */
    private final CheckoutQuery query;
    /** client-master 首次返回的主档候选。 */
    private final ClientMasterCandidates master;
    /** 本库同一只读快照取得的商务事实。 */
    private final CommercialCheckoutFacts facts;
    /** client-master 返回的合同参与方资料。 */
    private final List<PartyProfileFacts> partyProfiles;

    /**
     * 一次接收全部事实；主档 Client 身份必须与请求一致。
     *
     * @param query 已校验的解析查询
     * @param master 主档候选
     * @param facts 本库商务事实
     * @param partyProfiles 合同参与方资料
     */
    public CheckoutResolutionContext(CheckoutQuery query, ClientMasterCandidates master, CommercialCheckoutFacts facts,
        List<PartyProfileFacts> partyProfiles) {
        this.query = Objects.requireNonNull(query);
        this.master = Objects.requireNonNull(master);
        if (master.client() == null || !Objects.equals(master.client().clientId(), query.clientId())) {
            throw new FailException(CLIENT_NOT_FOUND);
        }
        this.facts = Objects.requireNonNull(facts);
        this.partyProfiles = List.copyOf(partyProfiles);
    }

    /** 按授权优先级惰性构造完整候选，低优先级合同不会提前影响已胜出者。 */
    public Iterable<CheckoutCandidateContext> candidates() {
        Set<BusinessChangeRecord> roots = revisions();
        return () -> master.candidates().stream().map(candidate -> buildCandidate(candidate, roots))
            .filter(Objects::nonNull).iterator();
    }

    /**
     * 外部资格检查后本库的新事实必须保留原查询、原选择和所有合同根
     * <li>主档根与选定授权由 client-master verify 复核；这里只复核本库合同根与选定候选。</li>
     */
    public void verifyUnchanged(CheckoutResolutionContext current, CheckoutCandidateContext selected) {
        if (!query.equals(current.query) || !query.equals(selected.query()) || !revisions().equals(current.revisions())
            || !current.stillAvailable(selected)) {
            throw new FailException(CCT_CONTEXT_CHANGED);
        }
    }

    /** 原合同未终止且同 OE 的候选与原选择完全相同；不能用另一 OE 或后继合同代替。 */
    private boolean stillAvailable(CheckoutCandidateContext selected) {
        if (facts.agreements().stream().filter(value -> selected.referencesAgreement(value.agreementId()))
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

    /** 合同根使用原记录的集合比较，顺序不影响复核，重复或无效根明确报错。 */
    private Set<BusinessChangeRecord> revisions() {
        Set<String> identities = new HashSet<>();
        for (BusinessChangeRecord root : facts.roots()) {
            require(root.recordType() != null && root.aggregateRootType() != null
                && root.recordType().getCode().equals(root.aggregateRootType().getCode())
                && Objects.equals(root.recordId(), root.aggregateRootId()) && root.currentVersion() != null
                && root.currentVersion() > 0 && identities.add(root.recordType().getCode() + ":" + root.recordId()));
        }
        return Set.copyOf(facts.roots());
    }

    /** 完整商务资格通过后一次构造候选上下文，不在后续步骤逐层补字段。 */
    private CheckoutCandidateContext buildCandidate(Candidate candidate, Set<BusinessChangeRecord> roots) {
        var client = master.client();
        Agreement clientAgreement = clientAgreement(candidate);
        if (clientAgreement == null) {
            return null;
        }
        CheckoutSubscriptionContext subscription =
            CheckoutSubscriptionContext.resolve(this, clientAgreement, candidate.entityNo());
        if (subscription == null) {
            return null;
        }
        CheckoutPricingContext clientTerms =
            CheckoutPricingContext.resolve(this, clientAgreement, candidate.entityNo(), "CLIENT_PRICING");
        if (clientTerms == null) {
            return null;
        }
        CheckoutCandidateContext.PartnerTerms partner = partner(candidate);
        if ("PARTNER".equals(candidate.sourceType()) && partner == null) {
            return null;
        }
        CheckoutCandidateContext.Legal legal = legal(candidate, clientAgreement);
        if (legal == null) {
            return null;
        }
        CheckoutCandidateContext result = new CheckoutCandidateContext(query, client, master.classification(), candidate,
            clientAgreement, clientTerms, subscription, partner, legal, masterRoot("CLIENT", id(client.clientId())),
            masterRoot("OPERATING_ENTITY", candidate.entityNo()), root(roots, id(clientAgreement.agreementId())),
            partner == null ? null : root(roots, id(partner.agreement().agreementId())));
        result.validateEvidence();
        return result;
    }

    /**
     * 与 Client 和 OE 准确匹配、已签署且资金模式本期可受理的 CLIENT 合同；不存在或资金模式不受理时候选落空。
     * <li>FEES_ONLY 本期不接受（technique §2.5），落空并记录日志，不改变合同本身的合法性判断。</li>
     */
    private Agreement clientAgreement(Candidate candidate) {
        var client = master.client();
        Agreement agreement = unique(facts.agreements(),
            value -> code(value.agreementType(), "CLIENT") && Objects.equals(value.clientId(), client.clientId())
                && Objects.equals(value.entityNo(), candidate.entityNo()) && agreementApplicable(value));
        if (agreement == null) {
            return null;
        }
        require(agreement.localPartnerId() == null && agreement.businessMode() != null && agreement.fundMode() != null
            && agreement.pricingAuthority() != null);
        requireSigned(agreement, client.partyId(), "CLIENT", candidate.oePartyId());
        if (agreement.fundMode() != FundMode.SETTLE_THROUGH_PLATFORM) {
            LOG.info("checkout 候选落空：资金模式本期不受理 clientId={} entityNo={} agreementId={} fundMode={}",
                client.clientId(), candidate.entityNo(), agreement.agreementId(), agreement.fundMode().getCode());
            return null;
        }
        return agreement;
    }

    /** PARTNER 来源的合同、双条款与唯一平台签约方；其他来源为空。 */
    private CheckoutCandidateContext.PartnerTerms partner(Candidate candidate) {
        if (!"PARTNER".equals(candidate.sourceType())) {
            return null;
        }
        Long partnerId = candidate.sourcePartnerId();
        LocalPartner partner = candidate.localPartner();
        require(partner != null && partnerId != null && partnerId.equals(partner.localPartnerId()));
        if (!"ACTIVE".equals(partner.status())) {
            return null;
        }
        Set<Long> covered = facts.agreementEntities().stream().filter(a -> a.entityNo().equals(candidate.entityNo()))
            .map(AgreementOperatingEntity::agreementId).collect(Collectors.toSet());
        Agreement agreement = unique(facts.agreements(),
            a -> code(a.agreementType(), "PARTNER") && Objects.equals(a.localPartnerId(), partnerId)
                && covered.contains(a.agreementId()) && agreementApplicable(a));
        if (agreement == null) {
            return null;
        }
        require(agreement.clientId() == null && agreement.entityNo() == null);
        requireSigned(agreement, partner.partyId(), "LOCAL_PARTNER", candidate.oePartyId());
        CheckoutPricingContext share = CheckoutPricingContext.resolve(this, agreement, candidate.entityNo(), "PARTNER_SHARE");
        CheckoutPricingContext charge =
            CheckoutPricingContext.resolve(this, agreement, candidate.entityNo(), "PLATFORM_SERVICE_CHARGE");
        if (share == null || charge == null) {
            return null;
        }
        AgreementParty signatory = unique(facts.agreementParties(),
            p -> p.agreementId().equals(agreement.agreementId()) && code(p.partyRole(), "PLATFORM_SIGNATORY"));
        require(signatory != null);
        AgreementOperatingEntity association = unique(facts.agreementEntities(),
            a -> a.agreementId().equals(agreement.agreementId()) && a.entityNo().equals(candidate.entityNo()));
        return new CheckoutCandidateContext.PartnerTerms(partner, agreement, association, share, charge, signatory);
    }

    /** 合同必须由所有者与 OE 签署，同一 Party 的多角色共用一份签署事实。 */
    private void requireSigned(Agreement agreement, Long ownerParty, String ownerRole, Long entityParty) {
        List<AgreementParty> parties =
            facts.agreementParties().stream().filter(p -> p.agreementId().equals(agreement.agreementId())).toList();
        require(!parties.isEmpty());
        require(parties.stream().anyMatch(p -> code(p.partyRole(), ownerRole) && p.partyId().equals(ownerParty)));
        require(
            parties.stream().filter(p -> code(p.partyRole(), ownerRole)).allMatch(p -> p.partyId().equals(ownerParty)));
        List<AgreementDocumentRef> documents =
            facts.agreementDocuments().stream().filter(d -> d.agreementId().equals(agreement.agreementId())).toList();
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

    /** 同一 Party 的签署证据、引用资料与签署文件校验。 */
    private void validateSignature(Agreement agreement, List<AgreementParty> group) {
        AgreementParty first = group.getFirst();
        require(first.acceptedTime() != null && first.acceptanceMethod() != null && first.acceptanceReference() != null
            && first.acceptanceRecordedBy() != null && first.acceptanceRecordedAt() != null
            && !first.acceptedTime().isAfter(agreement.signedTime()));
        Set<String> roles = new HashSet<>();
        for (AgreementParty party : group) {
            require(roles.add(party.partyRole().getCode()) && sameSignature(first, party));
            PartyProfileFacts profile = unique(partyProfiles, p -> p.profileId().equals(party.partyProfileId()));
            require(profile != null && profile.partyId().equals(party.partyId()) && profile.approvedTime() != null
                && !profile.approvedTime().isAfter(party.acceptedTime()) && profile.draftApplicationId() == null
                && validHash(profile.contentHash()));
        }
        require(group.stream().filter(p -> Objects.equals(p.acceptanceRefSlot(), 1)).count() == 1);
        Long firstId = group.stream().map(AgreementParty::agreementPartyId).min(Long::compare).orElseThrow();
        require(group.stream().allMatch(p -> p.agreementPartyId().equals(firstId)
            ? Objects.equals(p.acceptanceRefSlot(), 1) : p.acceptanceRefSlot() == null));
        AgreementDocumentRef document =
            unique(facts.agreementDocuments(), d -> d.agreementDocumentId().equals(first.evidenceDocumentId()));
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

    /** 三份法律文件与卖方披露取候选自带记录，按原规则选择唯一适用项；缺任一项落到下一候选。 */
    private CheckoutCandidateContext.Legal legal(Candidate candidate, Agreement agreement) {
        List<LegalDocument> documents = new ArrayList<>();
        for (String type : List.of("CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE")) {
            LegalDocument document = unique(candidate.legalDocuments(), d -> type.equals(d.documentType())
                && legalApplicable(d.status(), d.effectiveFrom(), d.effectiveTo(), d.approvedTime(), d.publishedTime()));
            if (document == null) {
                return null;
            }
            require(validHash(document.contentHash()) && document.contentFileId() != null);
            documents.add(document);
        }
        SellerDisclosure seller = unique(candidate.sellerDisclosures(),
            s -> legalApplicable(s.status(), s.effectiveFrom(), s.effectiveTo(), s.approvedTime(), s.publishedTime()));
        if (seller == null) {
            return null;
        }
        List<PartyProfileFacts> parties =
            facts.agreementParties().stream().filter(party -> party.agreementId().equals(agreement.agreementId()))
                .map(party -> unique(partyProfiles, profile -> profile.profileId().equals(party.partyProfileId())))
                .distinct().sorted(Comparator.comparing(PartyProfileFacts::profileId)).toList();
        return new CheckoutCandidateContext.Legal(documents, seller, parties);
    }

    /** 已批准、已发布且在商业时点有效的法律内容。 */
    private boolean legalApplicable(String status, Instant from, Instant to, Instant approved, Instant published) {
        return ("ACTIVE".equals(status) || "EXPIRED".equals(status)) && approved != null && published != null
            && !approved.isAfter(query.asOf()) && !published.isAfter(query.asOf())
            && applicable(from, to, query.asOf());
    }

    /** 已签署、已激活且在商业时点有效的合同。 */
    private boolean agreementApplicable(Agreement agreement) {
        return !code(agreement.status(), "DRAFT") && !code(agreement.status(), "PENDING_SIGNATURE")
            && !code(agreement.status(), "CANCELLED") && agreement.signedTime() != null
            && agreement.activatedTime() != null && !agreement.signedTime().isAfter(query.asOf())
            && !agreement.activatedTime().isAfter(query.asOf())
            && applicable(agreement.effectiveFrom(), agreement.effectiveTo(), query.asOf());
    }

    /** 对外引用的合同根必须真实存在，不能以零版本补齐。 */
    private static BusinessChangeRecord root(Set<BusinessChangeRecord> roots, String identity) {
        BusinessChangeRecord result = unique(roots.stream().toList(),
            value -> code(value.recordType(), "AGREEMENT") && identity.equals(value.recordId()));
        require(result != null);
        return result;
    }

    /** client-master 返回的 Client 与 OE 根必须真实存在。 */
    private RootVersion masterRoot(String type, String identity) {
        RootVersion result = master.root(type, identity);
        require(result != null && result.version() > 0);
        return result;
    }

    /** 时间窗口合法且包含给定时点；窗口本身非法时报冲突。 */
    static boolean applicable(Instant from, Instant to, Instant time) {
        require(from != null && (to == null || from.isBefore(to)));
        return !from.isAfter(time) && (to == null || time.isBefore(to));
    }

    /** 小写十六进制 SHA-256。 */
    static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    /** 枚举 Code 比较。 */
    static boolean code(CodeEnum value, String code) {
        return value != null && value.getCode().equals(code);
    }

    /** 正数 ID 的十进制文本；空值或非正数报冲突。 */
    static String id(Long value) {
        require(value != null && value > 0);
        return value.toString();
    }

    /** 至多一个匹配；多于一个报冲突。 */
    static <T> T unique(List<T> values, Predicate<T> predicate) {
        List<T> matches = values.stream().filter(predicate).toList();
        require(matches.size() <= 1);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /** 商务配置冲突。 */
    static void require(boolean condition) {
        if (!condition) {
            throw new FailException(CCT_CONTEXT_AMBIGUOUS);
        }
    }
}
