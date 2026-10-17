package com.paytech.client.master.domain.checkout;

import static com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.*;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 一次候选查询的客户主档原始事实；只做候选构造和一致性判断，不持有服务、事务或执行回调。
 * <li>校验规则自原 CCT 单库解析的 candidates / eligibleEntities 原样搬入（design/06 §2.1）；合同、定价与法律文件是否适用由
 * commercial 判断。</li>
 */
@Getter
@Accessors(fluent = true)
public final class CheckoutEligibilityContext {
    /** 本次查询的原始业务条件与冻结时点。 */
    private final CheckoutCandidateQuery query;
    /** 已按请求身份取得的 Client 原对象。 */
    private final Client client;
    /** 同一读事务取得的经营资料及分类确认原对象。 */
    private final List<ClientBusinessProfile> businessProfiles;
    /** 同一读事务取得的经营分类原对象。 */
    private final List<ClientBusinessClassification> classifications;
    /** 同一读事务取得的 Client/OE 授权关联原对象。 */
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
    /** 同一读事务取得的 Client 主体资料原对象。 */
    private final List<PartyProfile> partyProfiles;
    /** 同一读事务取得的消费者法律文件原对象。 */
    private final List<ConsumerLegalDocument> legalDocuments;
    /** 同一读事务取得的卖方披露原对象。 */
    private final List<SellerDisclosure> sellerDisclosures;
    /** 同一读事务取得的影响候选与优先级的业务根修订原对象。 */
    private final List<BusinessChangeRecord> roots;
    /** 同一读事务取得的 Client 已存卡开关。 */
    private final boolean storedCardEnabled;

    /** 一次接收同一事务取得的全部原始事实，空列表仅表示对应查询没有返回记录。 */
    @Builder
    private CheckoutEligibilityContext(CheckoutCandidateQuery query, Client client,
        List<ClientBusinessProfile> businessProfiles, List<ClientBusinessClassification> classifications,
        List<ClientEntityAffiliation> affiliations, List<OperatingEntity> entities, List<EntitySourceRelation> sources,
        List<OperatingEntityScope> scopes, List<OperatingEntityApplication> scopeApplications,
        List<LocalPartner> partners, List<PartyProfile> partyProfiles, List<ConsumerLegalDocument> legalDocuments,
        List<SellerDisclosure> sellerDisclosures, List<BusinessChangeRecord> roots, boolean storedCardEnabled) {
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
        this.partyProfiles = List.copyOf(partyProfiles);
        this.legalDocuments = List.copyOf(legalDocuments);
        this.sellerDisclosures = List.copyOf(sellerDisclosures);
        this.roots = List.copyOf(roots);
        this.storedCardEnabled = storedCardEnabled;
    }

    /**
     * 按授权优先级构造商业时点的全部主档候选
     * <li>Client 未激活或分类未就绪时整体拒绝为 CLIENT_NOT_ELIGIBLE；数据冲突为 CANDIDATE_CONTEXT_AMBIGUOUS。</li>
     * <li>单个 OE 不满足主档条件时直接落选，不影响其他候选。</li>
     */
    public CheckoutEligibility eligibility() {
        if (!code(client.status(), "ACTIVE")) {
            throw new FailException(CLIENT_NOT_ELIGIBLE);
        }
        ClientBusinessClassification classification = classification(query.asOf());
        if (classification == null) {
            throw new FailException(CLIENT_NOT_ELIGIBLE);
        }
        Set<BusinessChangeRecord> revisions = revisions();
        List<EntityCandidate> candidates = eligibleEntities(classification, query.asOf()).stream()
            .map(entity -> candidate(classification, entity, revisions)).toList();
        PartyProfile profile = unique(partyProfiles, p -> Objects.equals(p.partyId(), client.partyId()));
        return new CheckoutEligibility(client, profile, classification, candidates, scopeEntityNos(),
            scopePartnerIds(), sortedRoots(revisions), storedCardEnabled);
    }

    /**
     * 外部资格检查后核对首次查询的根版本与选定候选
     * <li>根版本变化时直接返回变化项，不再评估候选（与原 verifyUnchanged 的短路顺序一致）。</li>
     *
     * @param verification 首次根版本、选定授权与候选摘要
     * @param now 复核时点
     * @return 变化项；空列表表示未变化
     */
    public List<String> changes(CandidateVerification verification, Instant now) {
        Set<String> current = new TreeSet<>();
        for (BusinessChangeRecord root : revisions()) {
            current.add(rootKey(root.recordType().getCode(), root.recordId(), root.historyId(), root.currentVersion()));
        }
        if (!current.equals(verification.expectedRoots())) {
            return changedRoots(current, verification.expectedRoots());
        }
        if (!stillAvailable(verification, now)) {
            return List.of("CANDIDATE:" + verification.selected().entityNo());
        }
        return List.of();
    }

    /**
     * 根集合比较使用的稳定键。
     *
     * @param type 根类型 Code
     * @param recordId 业务记录 ID
     * @param historyId 根历史 ID
     * @param version 当前修订
     * @return {@code type:recordId:historyId:version}
     */
    public static String rootKey(String type, String recordId, Long historyId, long version) {
        return type + ":" + recordId + ":" + historyId + ":" + version;
    }

    /** 两个根集合的差异，按 {@code type:recordId} 去重排序。 */
    private static List<String> changedRoots(Set<String> current, Set<String> expected) {
        Set<String> changed = new TreeSet<>();
        for (String key : current) {
            if (!expected.contains(key)) {
                changed.add(rootIdentity(key));
            }
        }
        for (String key : expected) {
            if (!current.contains(key)) {
                changed.add(rootIdentity(key));
            }
        }
        return List.copyOf(changed);
    }

    /** 去掉历史与版本，只保留根类型与记录 ID。 */
    private static String rootIdentity(String key) {
        String[] parts = key.split(":", -1);
        return parts.length < 2 ? key : parts[0] + ":" + parts[1];
    }

    /** 当前状态和原准确引用都需有效；不能用另一 OE 或同 OE 的后继授权代替。 */
    private boolean stillAvailable(CandidateVerification verification, Instant now) {
        if (!code(client.status(), "ACTIVE")) {
            return false;
        }
        ClientBusinessClassification current = classification(now);
        if (current == null || eligibleEntities(current, now).stream()
            .noneMatch(entity -> verification.selected().sameAuthorization(entity))) {
            return false;
        }
        for (EntityCandidate candidate : eligibility().candidates()) {
            if (candidate.entityNo().equals(verification.selected().entityNo())) {
                return candidate.digest().equals(verification.candidateDigest());
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

    /** 主体资料与分类确认都早于给定时点时返回唯一 Primary 分类，否则为 null。 */
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

    /** 有效 Affiliation 按优先级升序逐个校验，返回主档条件全部通过的 OE。 */
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

    /** 单个 OE 的状态、唯一来源、准确 Scope 与 Scope 审批校验。 */
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

    /** 来源关系的形态必须与来源类型一致。 */
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

    /**
     * 一次构造主档候选：Partner、法律文件与卖方披露只取原记录，适用性与缺失判断留给 commercial 按原顺序执行。
     */
    private EntityCandidate candidate(ClientBusinessClassification classification, EntityEligibility entity,
        Set<BusinessChangeRecord> revisions) {
        LocalPartner partner = null;
        if (code(entity.source().sourceType(), "PARTNER")) {
            long partnerId = entity.source().sourcePartnerId();
            partner = unique(this.partners, p -> p.localPartnerId() == partnerId);
        }
        String entityNo = entity.entity().entityNo();
        List<ConsumerLegalDocument> documents = legalDocuments.stream().filter(d -> d.entityNo().equals(entityNo)
            && d.countryCode().equals(query.country()) && d.locale().equals(query.locale())).toList();
        List<SellerDisclosure> sellers = sellerDisclosures.stream().filter(s -> s.entityNo().equals(entityNo)
            && s.countryCode().equals(query.country()) && s.locale().equals(query.locale())).toList();
        BusinessChangeRecord clientRoot = root(revisions, "CLIENT", client.clientId().toString());
        BusinessChangeRecord entityRoot = root(revisions, "OPERATING_ENTITY", entityNo);
        String digest = digest(Arrays.asList(client, classification, entity.affiliation(), entity.entity(),
            entity.source(), entity.scope(), partner, documents, sellers, clientRoot, entityRoot));
        return new EntityCandidate(entity, partner, documents, sellers, digest);
    }

    /** 该 Client 全部 Affiliation 的 OE 编号，替代商务查询原来的 Affiliation 子查询。 */
    private List<String> scopeEntityNos() {
        return affiliations.stream().map(ClientEntityAffiliation::entityNo).filter(Objects::nonNull).distinct()
            .sorted().toList();
    }

    /** 上述 OE 的全部 PARTNER 来源 Local Partner，替代商务查询原来的来源关系子查询。 */
    private List<Long> scopePartnerIds() {
        Set<String> affiliated = new HashSet<>(scopeEntityNos());
        return sources.stream().filter(s -> affiliated.contains(s.entityNo()) && code(s.sourceType(), "PARTNER"))
            .map(EntitySourceRelation::sourcePartnerId).filter(Objects::nonNull).distinct().sorted().toList();
    }

    /** 根按类型与记录 ID 稳定排序，便于对外输出。 */
    private static List<BusinessChangeRecord> sortedRoots(Set<BusinessChangeRecord> revisions) {
        return revisions.stream().sorted(Comparator.comparing((BusinessChangeRecord r) -> r.recordType().getCode())
            .thenComparing(BusinessChangeRecord::recordId)).toList();
    }

    /** 读取指定根；不存在时为 null，由 commercial 在候选胜出时按原规则报冲突。 */
    private static BusinessChangeRecord root(Set<BusinessChangeRecord> revisions, String type, String identity) {
        return unique(revisions.stream().toList(),
            value -> code(value.recordType(), type) && identity.equals(value.recordId()));
    }

    /** 候选全部主档原记录的 SHA-256 摘要；记录的字符串形式由本服务同一版本代码产生，两次查询可比较。 */
    private static String digest(List<Object> facts) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(facts.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
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

    /** 至多一个匹配；多于一个报冲突。 */
    static <T> T unique(List<T> values, Predicate<T> predicate) {
        List<T> matches = values.stream().filter(predicate).toList();
        require(matches.size() <= 1);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /** 主档配置冲突。 */
    static void require(boolean condition) {
        if (!condition) {
            throw new FailException(CANDIDATE_CONTEXT_AMBIGUOUS);
        }
    }

    /** 原准入对象的组合；没有与对象重复的身份字段。 */
    @Value
    @Accessors(fluent = true)
    public static class EntityEligibility {
        /** Client/OE 原关联。 */
        ClientEntityAffiliation affiliation;
        /** 原经营主体。 */
        OperatingEntity entity;
        /** 原来源关系。 */
        EntitySourceRelation source;
        /** 批准的准确范围。 */
        OperatingEntityScope scope;
    }
}
