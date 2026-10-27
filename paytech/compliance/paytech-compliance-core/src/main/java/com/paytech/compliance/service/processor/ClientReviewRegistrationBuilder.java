package com.paytech.compliance.service.processor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;
import com.paytech.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.compliance.core.dal.dataobject.CaseRequirement;
import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewCaseBundle;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest.Document;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest.Relation;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest.Subject;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.AllArgsConstructor;

/**
 * I01 建案的纯内存构造：按冻结程序实例化案件、主体、材料要求、证据与检查，并按 S-01 从上一轮案件复用
 * <li>复用：同一材料槽本轮提交的文件与上一轮已接受证据同文件同摘要时直接 ACCEPTED；本轮未提交该槽时复制上一轮已接受证据。</li>
 * <li>复用：上一轮同代码检查为 COMPLETED + PASS 且 valid_until 晚于当前时间时复制结论；筛查与风险评级不复用。</li>
 * <li>不访问数据库或外部服务；版本、唯一性由保存时的唯一键与事务保证。</li>
 */
@Component
@AllArgsConstructor
class ClientReviewRegistrationBuilder {
    /** 关系类型到依赖类型；目录中的其他关系类型统一为 RELATED_PERSON_KYC。 */
    static final Map<String, String> DEPENDENCY_TYPES = Map.of("UBO", "UBO_KYC", "OWNERSHIP", "UBO_KYC", "CONTROL", "UBO_KYC",
        "DIRECTOR", "DIRECTOR_KYC", "SIGNATORY", "SIGNATORY_KYC");

    /** 未映射关系类型的依赖类型。 */
    static final String DEFAULT_DEPENDENCY = "RELATED_PERSON_KYC";

    private final ComplianceIdentifierGenerator ids;

    /**
     * 构造一个案件
     *
     * @param request I01 输入
     * @param subject 受审主体
     * @param roleId 角色 ID：主案件为 Client ID，子案件为 Party ID
     * @param program 解析出的程序
     * @param previous 上一轮同级案件；首次提交为空
     * @param previousFamily 上一轮案件全貌；首次提交为空
     * @param now 当前时间
     * @return 待保存的案件
     */
    ClientReviewCaseBundle bundle(RegisterClientReviewRequest request, Subject subject, long roleId, ResolvedProgram program,
        DueDiligenceCase previous, ClientReviewFamily previousFamily, Instant now) {
        boolean primary = ReviewCodes.SUBJECT_PRIMARY.equals(subject.getSubjectRole());
        DueDiligenceCase reviewCase = reviewCase(request, subject, primary ? ReviewCodes.ROLE_CLIENT : ReviewCodes.ROLE_RELATED_PERSON,
            roleId, program, previous, now);
        CaseSubject caseSubject = caseSubject(request, subject, reviewCase, primary, now);
        List<CaseRequirement> requirements = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        for (ProgramRequirement template : program.getRequirements()) {
            CaseRequirement requirement = requirement(reviewCase, template, now);
            List<EvidenceRef> rows = evidence(request, subject, caseSubject, requirement, template,
                previousAccepted(previousFamily, previous, template.getRequirementCode()), previous, now);
            settleRequirement(requirement, rows, previous);
            requirements.add(requirement);
            evidence.addAll(rows);
        }
        checkDocuments(request, subject, program);
        List<CaseCheck> checks = program.getChecks().stream()
            .map(template -> check(reviewCase, caseSubject, template, previousPass(previousFamily, previous, template, now), now))
            .toList();
        return new ClientReviewCaseBundle(reviewCase, caseSubject, requirements, evidence, checks,
            stateChange(reviewCase, request.getSourceSubmissionId(), now));
    }

    /** 新案件行：APPLICATION 来源、SUBMITTED、进行中占位，冻结辖区、档次、责任方与轮次。 */
    DueDiligenceCase reviewCase(RegisterClientReviewRequest request, Subject subject, String roleType, long roleId,
        ResolvedProgram program, DueDiligenceCase previous, Instant now) {
        DueDiligenceCase row = new DueDiligenceCase();
        row.setCaseId(ids.nextIdentifier());
        row.setCaseNo("DD" + row.getCaseId());
        row.setCaseType(ReviewCodes.CASE_TYPE_ONBOARDING);
        row.setRoleType(roleType);
        row.setRoleId(roleId);
        row.setQualificationType(subject.getQualificationType());
        row.setDdProgramVersionId(program.versionId());
        row.setJurisdictionCode(request.getJurisdictionCode());
        row.setTier(program.getVersion().getTier());
        row.setCaseOrigin("APPLICATION");
        row.setSourceNamespace(ReviewCodes.NAMESPACE_CLIENT_APPLICATION);
        row.setSourceApplicationId(request.getApplicationId());
        row.setSourceSubmissionId(request.getSourceSubmissionId());
        row.setRoundNo(previous == null ? 1 : previous.getRoundNo() + 1);
        row.setPreviousCaseId(previous == null ? null : previous.getCaseId());
        row.setResponsiblePartnerId(request.getResponsiblePartnerId());
        row.setStatus(DueDiligenceCaseStatus.SUBMITTED.getCode());
        row.setActiveSlot(1);
        row.setSubmittedTime(request.getSubmittedAt());
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** PRIMARY 主体；关联人员冻结其第一条关系的持股、UBO 依据与职务。 */
    CaseSubject caseSubject(RegisterClientReviewRequest request, Subject subject, DueDiligenceCase reviewCase, boolean primary,
        Instant now) {
        CaseSubject row = new CaseSubject();
        row.setCaseSubjectId(ids.nextIdentifier());
        row.setCaseId(reviewCase.getCaseId());
        row.setPartyId(subject.getPartyId());
        row.setSubjectRole(ReviewCodes.SUBJECT_PRIMARY);
        row.setPrimarySlot(1);
        row.setSourceProfileId(subject.getProfileId());
        row.setRequiredFlag(1);
        row.setCreateTime(now);
        if (!primary) {
            request.getRelations().stream().filter(relation -> relation.getRelatedPartyId() == subject.getPartyId()).findFirst()
                .ifPresent(relation -> {
                    row.setUboBasis(relation.getUboBasis());
                    row.setJobTitle(relation.getJobTitle());
                });
        }
        return row;
    }

    /** 一项材料要求，初始 PENDING。 */
    CaseRequirement requirement(DueDiligenceCase reviewCase, ProgramRequirement template, Instant now) {
        CaseRequirement row = new CaseRequirement();
        row.setRequirementId(ids.nextIdentifier());
        row.setCaseId(reviewCase.getCaseId());
        row.setProgramRequirementId(template.getProgramRequirementId());
        row.setStatus(ReviewCodes.PENDING);
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 本主体本材料槽的证据：本轮文件与上一轮已接受证据相同则直接接受；本轮未提交则复制上一轮已接受证据。 */
    List<EvidenceRef> evidence(RegisterClientReviewRequest request, Subject subject, CaseSubject caseSubject,
        CaseRequirement requirement, ProgramRequirement template, List<EvidenceRef> accepted, DueDiligenceCase previous, Instant now) {
        List<EvidenceRef> rows = new ArrayList<>();
        for (Document document : request.getDocuments()) {
            if (document.getSubjectPartyId() != subject.getPartyId()
                || !document.getRequirementCode().equals(template.getRequirementCode())) {
                continue;
            }
            if (!template.getDocumentTypes().contains(document.getDocumentType())) {
                throw new BizCheckFailException("材料类型不在材料槽允许范围: " + template.getRequirementCode() + "/" + document.getDocumentType());
            }
            EvidenceRef match = accepted.stream().filter(row -> row.getFileAddress().equals(document.getFileAddress())
                && row.getContentHash().equals(document.getContentHash())).findFirst().orElse(null);
            rows.add(match == null ? received(requirement, caseSubject, document, now)
                : copy(match, requirement, caseSubject, previous, now));
        }
        if (rows.isEmpty()) {
            accepted.forEach(row -> rows.add(copy(row, requirement, caseSubject, previous, now)));
        }
        return rows;
    }

    /** 本轮新提交的证据，待核验。 */
    EvidenceRef received(CaseRequirement requirement, CaseSubject caseSubject, Document document, Instant now) {
        EvidenceRef row = new EvidenceRef();
        row.setEvidenceId(ids.nextIdentifier());
        row.setRequirementId(requirement.getRequirementId());
        row.setCaseSubjectId(caseSubject.getCaseSubjectId());
        row.setFileAddress(document.getFileAddress());
        row.setContentHash(document.getContentHash());
        row.setDocumentType(document.getDocumentType());
        row.setIssueDate(document.getIssueDate());
        row.setExpiryDate(document.getExpiryDate());
        row.setStatus(ReviewCodes.RECEIVED);
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 复用上一轮已接受证据：保留文件、属性与核验人，标记来源案件。 */
    EvidenceRef copy(EvidenceRef source, CaseRequirement requirement, CaseSubject caseSubject, DueDiligenceCase previous, Instant now) {
        EvidenceRef row = new EvidenceRef();
        row.setEvidenceId(ids.nextIdentifier());
        row.setRequirementId(requirement.getRequirementId());
        row.setCaseSubjectId(caseSubject.getCaseSubjectId());
        row.setFileAddress(source.getFileAddress());
        row.setContentHash(source.getContentHash());
        row.setDocumentType(source.getDocumentType());
        row.setIssueDate(source.getIssueDate());
        row.setExpiryDate(source.getExpiryDate());
        row.setStatus(ReviewCodes.ACCEPTED);
        row.setReviewerId(source.getReviewerId());
        row.setReviewedTime(source.getReviewedTime());
        row.setReviewNote(source.getReviewNote());
        row.setReusedFromCaseId(previous.getCaseId());
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 要求状态由证据推导：有待核验为 RECEIVED，否则有已接受为 ACCEPTED，否则 PENDING；全部来自复用时标记来源案件。 */
    static void settleRequirement(CaseRequirement requirement, List<EvidenceRef> rows, DueDiligenceCase previous) {
        if (rows.stream().anyMatch(row -> ReviewCodes.RECEIVED.equals(row.getStatus()))) {
            requirement.setStatus(ReviewCodes.RECEIVED);
            return;
        }
        if (!rows.isEmpty()) {
            requirement.setStatus(ReviewCodes.ACCEPTED);
            requirement.setReusedFromCaseId(previous.getCaseId());
        }
    }

    /** 本主体的每份材料都必须对应程序中的材料槽。 */
    static void checkDocuments(RegisterClientReviewRequest request, Subject subject, ResolvedProgram program) {
        for (Document document : request.getDocuments()) {
            if (document.getSubjectPartyId() == subject.getPartyId() && program.requirement(document.getRequirementCode()).isEmpty()) {
                throw new BizCheckFailException("材料不属于当前尽调程序: " + document.getRequirementCode());
            }
        }
    }

    /** 一项检查：有可复用的上一轮 PASS 结论时直接完成，否则待录入；本期只支持人工检查。 */
    CaseCheck check(DueDiligenceCase reviewCase, CaseSubject caseSubject, ProgramCheck template, CaseCheck reusable, Instant now) {
        if (!ReviewCodes.CHECK_MODE_MANUAL.equals(template.getCheckMode())) {
            throw new ServiceUnavailableException();
        }
        CaseCheck row = new CaseCheck();
        row.setCheckId(ids.nextIdentifier());
        row.setCaseId(reviewCase.getCaseId());
        row.setCaseSubjectId(caseSubject.getCaseSubjectId());
        row.setProgramCheckId(template.getProgramCheckId());
        row.setCheckMode(ReviewCodes.CHECK_MODE_MANUAL);
        row.setProviderCode(ReviewCodes.CHECK_MODE_MANUAL);
        row.setRequestRef(Long.toString(row.getCheckId()));
        row.setStatus(ReviewCodes.CHECK_REQUESTED);
        row.setVersion(1);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        if (reusable != null) {
            row.setStatus(ReviewCodes.CHECK_COMPLETED);
            row.setOutcome(reusable.getOutcome());
            row.setReasonCode(reusable.getReasonCode());
            row.setEvidenceRef(reusable.getEvidenceRef());
            row.setNote(reusable.getNote());
            row.setReviewerId(reusable.getReviewerId());
            row.setReviewedTime(reusable.getReviewedTime());
            row.setValidUntil(reusable.getValidUntil());
            row.setReusedFromCaseId(reusable.getCaseId());
        }
        return row;
    }

    /** 初始状态变更：无 → SUBMITTED，由 client-master 提交触发。 */
    CaseStateChange stateChange(DueDiligenceCase reviewCase, String submissionId, Instant now) {
        CaseStateChange row = new CaseStateChange();
        row.setChangeId(ids.nextIdentifier());
        row.setCaseId(reviewCase.getCaseId());
        row.setToStatus(DueDiligenceCaseStatus.SUBMITTED.getCode());
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
        for (Relation relation : request.getRelations()) {
            if (relation.getRelatedPartyId() == child.getSubject().getPartyId()) {
                types.add(DEPENDENCY_TYPES.getOrDefault(relation.getRelationType(), DEFAULT_DEPENDENCY));
            }
        }
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

    /** 上一轮同级案件同代码材料槽下已接受的证据。 */
    static List<EvidenceRef> previousAccepted(ClientReviewFamily family, DueDiligenceCase previous, String requirementCode) {
        if (previous == null) {
            return List.of();
        }
        List<Long> requirementIds = family.getRequirements().stream()
            .filter(row -> row.getCaseId() == previous.getCaseId() && row.getRequirementCode().equals(requirementCode))
            .map(ReviewRequirementView::getRequirementId).toList();
        return family.getEvidence().stream().filter(row -> requirementIds.contains(row.getRequirementId())
            && ReviewCodes.ACCEPTED.equals(row.getStatus())).toList();
    }

    /** 上一轮同级案件同代码检查的有效 PASS 结论；没有返回 null。 */
    static CaseCheck previousPass(ClientReviewFamily family, DueDiligenceCase previous, ProgramCheck template, Instant now) {
        if (previous == null) {
            return null;
        }
        ResolvedProgram program = family.programOf(previous);
        return family.getChecks().stream().filter(row -> row.getCaseId() == previous.getCaseId())
            .filter(row -> program.getChecks().stream().anyMatch(item -> item.getProgramCheckId() == row.getProgramCheckId()
                && item.getCheckCode().equals(template.getCheckCode())))
            .filter(row -> ReviewCodes.CHECK_COMPLETED.equals(row.getStatus()) && ReviewCodes.OUTCOME_PASS.equals(row.getOutcome()))
            .filter(row -> row.getValidUntil() != null && row.getValidUntil().isAfter(now)).findFirst().orElse(null);
    }

    /** 上一轮家族中同一关联人员的子案件；没有返回 null。 */
    static DueDiligenceCase previousChild(ClientReviewFamily family, Subject subject) {
        if (family == null) {
            return null;
        }
        return family.getChildCases().stream().filter(row -> row.getRoleId() == subject.getPartyId()
            && Objects.equals(row.getQualificationType(), subject.getQualificationType())).findFirst().orElse(null);
    }

    /** 微秒精度的当前时间。 */
    static Instant micros(Instant now) {
        return now.truncatedTo(ChronoUnit.MICROS);
    }
}
