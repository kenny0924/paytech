package com.paytech.risk.compliance.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.risk.compliance.core.dal.dataobject.CaseRequirement;
import com.paytech.risk.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.risk.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.risk.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.risk.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.risk.compliance.core.dal.dataobject.MaterialProgram;
import com.paytech.risk.compliance.core.dal.dataobject.MaterialRequirement;
import com.paytech.risk.compliance.core.enums.MaterialQualificationType;
import com.paytech.risk.compliance.domain.ClientReviewCaseBundle;
import com.paytech.risk.compliance.domain.ClientReviewContext;
import com.paytech.risk.compliance.domainservice.policy.MaterialProgramResolver;
import com.paytech.risk.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.risk.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.risk.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.risk.compliance.domainservice.repository.MaterialRequirementsRepository;
import com.paytech.risk.compliance.domainservice.service.ClientReviewService;
import com.paytech.risk.compliance.service.processor.request.RegisterClientReviewRequest;
import com.paytech.risk.compliance.service.processor.request.RegisterClientReviewRequest.Document;
import com.paytech.risk.compliance.service.processor.request.RegisterClientReviewRequest.Relation;
import com.paytech.risk.compliance.service.processor.request.RegisterClientReviewRequest.Subject;
import com.paytech.risk.compliance.service.processor.response.RegisterClientReviewResponse;

import lombok.AllArgsConstructor;

/**
 * I01：按 CCT 来源提交编号登记 Client 入驻审核任务。
 * <li>主案件 role=CLIENT；每名关联人员一个 RELATED_PERSON_KYC 子案件，通过依赖关联主案件。</li>
 * <li>材料要求按当前通用程序修订实例化，每份材料成为对应主体的证据引用。</li>
 * <li>同一来源提交已登记时比较原任务与本次内容：一致返回原主任务，不一致 409；唯一键 uk_dd_case_source 是最终保护，不加 Redis 锁。</li>
 * <li>资料快照本期不做（2026-10-13 用户决定），不写 t_dd_party_snapshot，也不调用 Vault。</li>
 */
@Component
@AllArgsConstructor
public class RegisterClientReviewProcessor extends AbstractProcessor<RegisterClientReviewRequest, RegisterClientReviewResponse> {
    public static final String NAMESPACE = "CCT_CLIENT_APPLICATION";
    static final String PRIMARY = "PRIMARY";
    static final String RELATED_PERSON = "RELATED_PERSON";
    static final String SUBMITTED = "SUBMITTED";
    static final Map<String, String> DEPENDENCY_TYPES = Map.of("UBO", "UBO_KYC", "OWNERSHIP", "UBO_KYC", "CONTROL", "UBO_KYC",
        "DIRECTOR", "DIRECTOR_KYC", "SIGNATORY", "SIGNATORY_KYC");

    private final Clock clock;
    private final ClientReviewService reviews;
    private final EvidenceRefRepository evidence;
    private final MaterialProgramResolver resolver;
    private final DueDiligenceCaseRepository cases;
    private final ComplianceIdentifierGenerator ids;
    private final MaterialRequirementsRepository programs;

    /** 只依赖输入的结构关系：主体角色与资格、关系与材料的主体归属。 */
    @Override
    protected void validate(RegisterClientReviewRequest request) {
        boolean organization = "ORGANIZATION".equals(request.getClientType());
        if (!organization && !"INDIVIDUAL".equals(request.getClientType())) {
            throw new InvalidParameterException("客户类型无效");
        }
        List<Subject> subjects = request.getSubjects();
        Subject primary = subjects.getFirst();
        if (!PRIMARY.equals(primary.getSubjectRole())
            || !(organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC").equals(primary.getQualificationType())) {
            throw new InvalidParameterException("主主体角色或资格类型无效");
        }
        Set<Long> parties = new HashSet<>();
        for (Subject subject : subjects) {
            boolean related = subject != primary;
            if (!parties.add(subject.getPartyId()) || (related && (!organization || !RELATED_PERSON.equals(subject.getSubjectRole())
                || !"RELATED_PERSON_KYC".equals(subject.getQualificationType())))) {
                throw new InvalidParameterException("受审主体重复或关联人员无效");
            }
        }
        checkRelations(request, parties);
        if (request.getDocuments().stream().anyMatch(document -> !parties.contains(document.getSubjectPartyId()))) {
            throw new InvalidParameterException("材料所属主体不在受审范围");
        }
    }

    @Override
    protected RegisterClientReviewResponse processRequest(RegisterClientReviewRequest request) {
        // 查询原提交
        List<DueDiligenceCase> existing = submissionCases(request);
        if (!existing.isEmpty()) {
            return replay(request, existing);
        }

        // 构建上下文
        ClientReviewContext context = buildContext(request);

        // 保存上下文
        try {
            reviews.register(context);
        } catch (DuplicateKeyException collision) {
            return collision(request, collision);
        }

        // 响应处理
        return new RegisterClientReviewResponse(request.getSourceSubmissionId(), context.getMainCase().getReviewCase().getCaseId());
    }

    /** 每名关联人员至少一条关系，关系只能指向关联人员且类型受支持。 */
    void checkRelations(RegisterClientReviewRequest request, Set<Long> parties) {
        long primaryParty = request.getSubjects().getFirst().getPartyId();
        Set<Long> related = new HashSet<>();
        for (Relation relation : request.getRelations()) {
            if (relation.getRelatedPartyId() == primaryParty || !parties.contains(relation.getRelatedPartyId())
                || !DEPENDENCY_TYPES.containsKey(relation.getRelationType())) {
                throw new InvalidParameterException("关系指向的人员或类型无效");
            }
            related.add(relation.getRelatedPartyId());
        }
        if (related.size() != parties.size() - 1) {
            throw new InvalidParameterException("关联人员缺少关系");
        }
    }

    /** 读取通用程序、实例化各案件并一次构造完整上下文。 */
    ClientReviewContext buildContext(RegisterClientReviewRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Map<String, ProgramCatalog> catalogs = new HashMap<>();
        List<Subject> subjects = request.getSubjects();
        ClientReviewCaseBundle main = bundle(request, subjects.getFirst(), "CLIENT", request.getClientId(),
            catalogs.computeIfAbsent(subjects.getFirst().getQualificationType(), this::catalog), now);
        List<ClientReviewCaseBundle> children = new ArrayList<>();
        List<CaseDependency> dependencies = new ArrayList<>();
        for (Subject subject : subjects.subList(1, subjects.size())) {
            ClientReviewCaseBundle child = bundle(request, subject, RELATED_PERSON, subject.getPartyId(),
                catalogs.computeIfAbsent(subject.getQualificationType(), this::catalog), now);
            children.add(child);
            dependencies.addAll(dependencies(request, main, child, now));
        }
        return new ClientReviewContext(main, children, dependencies);
    }

    /** 某资格类型当前唯一批准的通用程序修订及其材料模板；缺失或损坏时不可用。 */
    ProgramCatalog catalog(String qualificationType) {
        MaterialQualificationType type = MaterialQualificationType.ofCode(qualificationType);
        List<MaterialProgram> candidates = programs.programs(resolver.resolve(type), qualificationType);
        if (candidates.size() != 1 || !candidates.getFirst().isApprovedAuthorityAt(clock.instant())) {
            throw new ServiceUnavailableException();
        }
        long versionId = candidates.getFirst().getProgramVersionId();
        Map<String, MaterialRequirement> byCode = new LinkedHashMap<>();
        for (MaterialRequirement requirement : programs.requirements(versionId)) {
            if (!requirement.isWellFormed() || byCode.put(requirement.getRequirementCode(), requirement) != null) {
                throw new ServiceUnavailableException();
            }
        }
        if (byCode.isEmpty()) {
            throw new ServiceUnavailableException();
        }
        return new ProgramCatalog(versionId, byCode);
    }

    /** 实例化单个案件、PRIMARY 主体、材料要求、证据与初始状态变更。 */
    ClientReviewCaseBundle bundle(RegisterClientReviewRequest request, Subject subject, String roleType, long roleId,
        ProgramCatalog catalog, Instant now) {
        DueDiligenceCase reviewCase = reviewCase(request, subject, roleType, roleId, catalog.versionId(), now);
        CaseSubject caseSubject = new CaseSubject();
        caseSubject.setCaseSubjectId(ids.nextIdentifier());
        caseSubject.setCaseId(reviewCase.getCaseId());
        caseSubject.setPartyId(subject.getPartyId());
        caseSubject.setSubjectRole(PRIMARY);
        caseSubject.setPrimarySlot(1);
        caseSubject.setSourceProfileId(subject.getProfileId());
        caseSubject.setRequiredFlag(1);
        caseSubject.setCreateTime(now);
        Map<String, CaseRequirement> requirements = requirements(reviewCase, catalog, now);
        List<EvidenceRef> evidenceRefs = evidence(request, subject, caseSubject, requirements, catalog, now);
        return new ClientReviewCaseBundle(reviewCase, caseSubject, List.copyOf(requirements.values()), evidenceRefs,
            stateChange(reviewCase, request.getSourceSubmissionId(), now));
    }

    /** 新案件行：APPLICATION 来源、SUBMITTED、进行中占位。 */
    DueDiligenceCase reviewCase(RegisterClientReviewRequest request, Subject subject, String roleType, long roleId, long versionId,
        Instant now) {
        DueDiligenceCase row = new DueDiligenceCase();
        row.setCaseId(ids.nextIdentifier());
        row.setCaseNo("DD" + row.getCaseId());
        row.setRoleType(roleType);
        row.setRoleId(roleId);
        row.setQualificationType(subject.getQualificationType());
        row.setDdProgramVersionId(versionId);
        row.setCaseOrigin("APPLICATION");
        row.setSourceNamespace(NAMESPACE);
        row.setSourceApplicationId(request.getApplicationId());
        row.setSourceSubmissionId(request.getSourceSubmissionId());
        row.setStatus(SUBMITTED);
        row.setActiveSlot(1);
        row.setSubmittedTime(request.getSubmittedAt());
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 按程序模板实例化要求；有材料的要求标记 RECEIVED。 */
    Map<String, CaseRequirement> requirements(DueDiligenceCase reviewCase, ProgramCatalog catalog, Instant now) {
        Map<String, CaseRequirement> rows = new LinkedHashMap<>();
        catalog.byCode().forEach((code, template) -> {
            CaseRequirement row = new CaseRequirement();
            row.setRequirementId(ids.nextIdentifier());
            row.setCaseId(reviewCase.getCaseId());
            row.setProgramRequirementId(template.getProgramRequirementId());
            row.setStatus("PENDING");
            row.setVersion(1);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.put(code, row);
        });
        return rows;
    }

    /** 本主体的材料必须对应程序中的要求代码与类型。 */
    List<EvidenceRef> evidence(RegisterClientReviewRequest request, Subject subject, CaseSubject caseSubject,
        Map<String, CaseRequirement> requirements, ProgramCatalog catalog, Instant now) {
        List<EvidenceRef> rows = new ArrayList<>();
        for (Document document : request.getDocuments()) {
            if (document.getSubjectPartyId() != subject.getPartyId()) {
                continue;
            }
            MaterialRequirement template = catalog.byCode().get(document.getRequirementCode());
            if (template == null || !template.getDocumentType().equals(document.getDocumentType())) {
                throw new BizCheckFailException("材料不属于当前通用材料程序：" + document.getRequirementCode());
            }
            CaseRequirement requirement = requirements.get(document.getRequirementCode());
            requirement.setStatus("RECEIVED");
            rows.add(evidenceRef(requirement, caseSubject, document, now));
        }
        return rows;
    }

    /** 单条证据引用。 */
    EvidenceRef evidenceRef(CaseRequirement requirement, CaseSubject caseSubject, Document document, Instant now) {
        EvidenceRef row = new EvidenceRef();
        row.setEvidenceId(ids.nextIdentifier());
        row.setRequirementId(requirement.getRequirementId());
        row.setCaseSubjectId(caseSubject.getCaseSubjectId());
        row.setFileId(document.getFileId());
        row.setContentHash(document.getContentHash());
        row.setStatus("RECEIVED");
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 初始状态变更：无 → SUBMITTED，由 CCT 提交触发。 */
    CaseStateChange stateChange(DueDiligenceCase reviewCase, String submissionId, Instant now) {
        CaseStateChange row = new CaseStateChange();
        row.setChangeId(ids.nextIdentifier());
        row.setCaseId(reviewCase.getCaseId());
        row.setToStatus(SUBMITTED);
        row.setActionType("REGISTER");
        row.setTriggerType("CCT_CLIENT_SUBMISSION");
        row.setTriggerId(submissionId);
        row.setAggregateVersion(1);
        row.setOccurredTime(now);
        row.setCreateTime(now);
        return row;
    }

    /** 主案件依赖子案件；同一人员的多种关系按依赖类型去重。 */
    List<CaseDependency> dependencies(RegisterClientReviewRequest request, ClientReviewCaseBundle main, ClientReviewCaseBundle child,
        Instant now) {
        Set<String> types = new LinkedHashSet<>();
        request.getRelations().stream().filter(relation -> relation.getRelatedPartyId() == child.getSubject().getPartyId())
            .forEach(relation -> types.add(DEPENDENCY_TYPES.get(relation.getRelationType())));
        return types.stream().map(type -> {
            CaseDependency row = new CaseDependency();
            row.setDependencyId(ids.nextIdentifier());
            row.setParentCaseId(main.getReviewCase().getCaseId());
            row.setChildCaseId(child.getReviewCase().getCaseId());
            row.setDependencyType(type);
            row.setRequiredFlag(1);
            row.setVersion(1);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
    }

    /** 同一来源提交的已登记案件。 */
    List<DueDiligenceCase> submissionCases(RegisterClientReviewRequest request) {
        return cases.findBySubmission(NAMESPACE, request.getApplicationId(), request.getSourceSubmissionId());
    }

    /** 已登记：主体与证据集合一致则返回原主任务，否则 409。 */
    RegisterClientReviewResponse replay(RegisterClientReviewRequest request, List<DueDiligenceCase> existing) {
        Set<String> expectedCases = new HashSet<>();
        expectedCases.add("CLIENT:" + request.getClientId() + ":" + request.getSubjects().getFirst().getQualificationType());
        request.getSubjects().subList(1, request.getSubjects().size())
            .forEach(subject -> expectedCases.add(RELATED_PERSON + ":" + subject.getPartyId() + ":" + subject.getQualificationType()));
        Set<String> actualCases = new HashSet<>();
        existing.forEach(row -> actualCases.add(row.getRoleType() + ":" + row.getRoleId() + ":" + row.getQualificationType()));
        Set<String> expectedEvidence = new HashSet<>();
        request.getDocuments().forEach(document -> expectedEvidence.add(document.getFileId() + ":" + document.getContentHash()));
        Set<String> actualEvidence = new HashSet<>();
        evidence.findByCaseIds(existing.stream().map(DueDiligenceCase::getCaseId).toList())
            .forEach(row -> actualEvidence.add(row.getFileId() + ":" + row.getContentHash()));
        if (!expectedCases.equals(actualCases) || !expectedEvidence.equals(actualEvidence)) {
            throw new ConcurrentRequestException("同一来源提交编号的内容不一致");
        }
        return existing.stream().filter(row -> "CLIENT".equals(row.getRoleType())).findFirst()
            .map(row -> new RegisterClientReviewResponse(request.getSourceSubmissionId(), row.getCaseId()))
            .orElseThrow(() -> new ConcurrentRequestException("同一来源提交编号的内容不一致"));
    }

    /** 并发首次登记由唯一键回滚后按原提交处理；主体已有其他进行中任务明确冲突。 */
    RegisterClientReviewResponse collision(RegisterClientReviewRequest request, DuplicateKeyException collision) {
        String reason = collision.getMostSpecificCause().getMessage();
        if (reason != null && reason.contains("uk_dd_case_source")) {
            List<DueDiligenceCase> existing = submissionCases(request);
            if (!existing.isEmpty()) {
                return replay(request, existing);
            }
        }
        if (reason != null && reason.contains("uk_dd_case_active")) {
            throw new ConcurrentRequestException("受审主体已有进行中的审核任务");
        }
        throw collision;
    }

    /**
     * 某资格类型的程序修订与材料模板
     *
     * @param versionId 生效程序修订 ID
     * @param byCode 按要求代码索引的模板
     */
    record ProgramCatalog(long versionId, Map<String, MaterialRequirement> byCode) {
    }
}
