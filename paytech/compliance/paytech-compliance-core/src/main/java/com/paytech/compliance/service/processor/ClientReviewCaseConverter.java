package com.paytech.compliance.service.processor;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ReturnError;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;
import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy;
import com.paytech.compliance.service.processor.response.ClientReviewCaseResponse;

/** 把模块内审核全貌转换为可跨模块的 Owner 结果；纯转换。 */
final class ClientReviewCaseConverter {
    private ClientReviewCaseConverter() {
    }

    /** 列表摘要。 */
    static ClientReviewCaseResponse summary(DueDiligenceCase row) {
        ClientReviewCaseResponse response = new ClientReviewCaseResponse();
        response.setCaseId(row.getCaseId());
        response.setCaseNo(row.getCaseNo());
        response.setCaseType(row.getCaseType());
        response.setApplicationId(row.getSourceApplicationId());
        response.setClientId(row.getRoleId());
        response.setSourceSubmissionId(row.getSourceSubmissionId());
        response.setQualificationType(row.getQualificationType());
        response.setJurisdictionCode(row.getJurisdictionCode());
        response.setProgramVersionId(row.getDdProgramVersionId());
        response.setTier(row.getTier());
        response.setRoundNo(row.getRoundNo());
        response.setPreviousCaseId(row.getPreviousCaseId());
        response.setResponsiblePartnerId(row.getResponsiblePartnerId());
        response.setStatus(row.getStatus());
        response.setVersion(row.getVersion());
        response.setSubmittedAt(row.getSubmittedTime());
        response.setDecidedAt(row.getDecisionTime());
        response.setReviewDueTime(row.getReviewDueTime());
        response.setRiskRating(row.getRiskRating());
        response.setRecommendation(row.getRecommendation());
        response.setPublicNote(row.getPublicNote());
        response.setReturnSection(row.getReturnSection());
        response.setOperatedBy(row.getOperatedBy());
        response.setOnBehalfOfPartnerId(row.getOnBehalfOfPartnerId());
        return response;
    }

    /** 详情；门禁按查看人计算四眼。 */
    static ClientReviewCaseResponse detail(ClientReviewFamily family, Long actorId, Instant now) {
        DueDiligenceCase main = family.getMainCase();
        ClientReviewCaseResponse response = summary(main);
        response.setFourEyesRequired(family.mainProgram().getVersion().requiresFourEyes());
        response.setRiskRatingNote(main.getRiskRatingNote());
        response.setRiskRatedBy(main.getRiskRatedBy());
        response.setRiskRatedAt(main.getRiskRatedTime());
        response.setRecommendedBy(main.getRecommendedBy());
        response.setRecommendedAt(main.getRecommendedTime());
        response.setRecommendationNote(main.getRecommendationNote());
        response.setRiskFactors(family.getRiskFactors().stream().map(row -> {
            ClientReviewCaseResponse.RiskFactorItem item = new ClientReviewCaseResponse.RiskFactorItem();
            item.setFactorCode(row.getFactorCode());
            item.setFactorValue(row.getFactorValue());
            item.setSource(row.getSource());
            item.setRecordedBy(row.getRecordedBy());
            item.setRecordedAt(row.getRecordedTime());
            return item;
        }).toList());
        response.setReturnErrors(errors(family.getReturnErrors(), main.getReviewDueTime()));
        response.setSubjects(family.allCases().stream().map(row -> subject(family, row)).toList());
        response.setClassifications(family.getClassifications().stream().map(row -> {
            ClientReviewCaseResponse.Classification item = new ClientReviewCaseResponse.Classification();
            item.setIndustryCode(row.getIndustryCode());
            item.setMccCode(row.getMccCode());
            item.setPrimary(Integer.valueOf(1).equals(row.getPrimarySlot()));
            return item;
        }).toList());
        response.setTimeline(timeline(family));
        response.setApprovalBlockers(ClientReviewApprovalPolicy.approvalBlockers(family, actorId, now));
        return response;
    }

    /** 退回错误。 */
    static List<ClientReviewCaseResponse.ErrorItem> errors(List<ReturnError> rows, Instant dueTime) {
        return rows.stream().map(row -> {
            ClientReviewCaseResponse.ErrorItem item = new ClientReviewCaseResponse.ErrorItem();
            item.setRequirementKey(row.getRequirementKey());
            item.setSubjectPartyId(row.getSubjectPartyId());
            item.setCode(row.getErrorCode());
            item.setReason(row.getReason());
            item.setRemediation(row.getRemediationCode());
            item.setDueTime(dueTime);
            return item;
        }).toList();
    }

    /** 时间线。 */
    static List<ClientReviewCaseResponse.TimelineItem> timeline(ClientReviewFamily family) {
        return family.getStateChanges().stream().map(row -> {
            ClientReviewCaseResponse.TimelineItem item = new ClientReviewCaseResponse.TimelineItem();
            item.setCaseId(row.getCaseId());
            item.setFromStatus(row.getFromStatus());
            item.setToStatus(row.getToStatus());
            item.setActionType(row.getActionType());
            item.setReasonCode(row.getReasonCode());
            item.setActorId(row.getActorId());
            item.setOperatedBy(row.getOperatedBy());
            item.setOnBehalfOfPartnerId(row.getOnBehalfOfPartnerId());
            item.setOccurredAt(row.getOccurredTime());
            return item;
        }).toList();
    }

    /** 单个案件的主体、材料、检查与筛查。 */
    static ClientReviewCaseResponse.Subject subject(ClientReviewFamily family, DueDiligenceCase row) {
        CaseSubject subject = family.subjectOf(row.getCaseId());
        ClientReviewCaseResponse.Subject item = new ClientReviewCaseResponse.Subject();
        item.setCaseId(row.getCaseId());
        item.setCaseStatus(row.getStatus());
        item.setPartyId(subject.getPartyId());
        item.setProfileId(subject.getSourceProfileId());
        item.setRoleType(row.getRoleType());
        item.setQualificationType(row.getQualificationType());
        item.setUboBasis(subject.getUboBasis());
        item.setJobTitle(subject.getJobTitle());
        item.setDependencyTypes(family.getDependencies().stream().filter(d -> d.getChildCaseId() == row.getCaseId())
            .map(d -> d.getDependencyType()).toList());
        item.setRequirements(family.getRequirements().stream().filter(r -> r.getCaseId() == row.getCaseId())
            .map(r -> requirement(family, r)).toList());
        item.setChecks(family.getChecks().stream().filter(c -> c.getCaseId() == row.getCaseId()).map(c -> check(family, row, c)).toList());
        item.setScreenings(family.getScreeningRuns().stream().filter(run -> run.getCaseId() == row.getCaseId())
            .map(run -> screening(family, row, run)).toList());
        return item;
    }

    /** 一项材料要求及其证据。 */
    static ClientReviewCaseResponse.Requirement requirement(ClientReviewFamily family, ReviewRequirementView view) {
        ClientReviewCaseResponse.Requirement item = new ClientReviewCaseResponse.Requirement();
        item.setRequirementId(view.getRequirementId());
        item.setRequirementCode(view.getRequirementCode());
        item.setDocumentTypes(view.getDocumentTypes());
        item.setRequired(view.isRequired());
        item.setWaivable(view.isWaivable());
        item.setMaxAgeDays(view.getMaxAgeDays());
        item.setStatus(view.getStatus());
        item.setWaivedBy(view.getWaivedBy());
        item.setWaivedReasonCode(view.getWaivedReasonCode());
        item.setWaivedAt(view.getWaivedTime());
        item.setReusedFromCaseId(view.getReusedFromCaseId());
        item.setEvidence(family.getEvidence().stream().filter(e -> e.getRequirementId() == view.getRequirementId())
            .map(ClientReviewCaseConverter::evidence).toList());
        return item;
    }

    /** 一份证据。 */
    static ClientReviewCaseResponse.Evidence evidence(EvidenceRef row) {
        ClientReviewCaseResponse.Evidence item = new ClientReviewCaseResponse.Evidence();
        item.setEvidenceId(row.getEvidenceId());
        item.setFileAddress(row.getFileAddress());
        item.setContentHash(row.getContentHash());
        item.setDocumentType(row.getDocumentType());
        item.setIssueDate(row.getIssueDate());
        item.setExpiryDate(row.getExpiryDate());
        item.setStatus(row.getStatus());
        item.setRejectReasonCode(row.getRejectReasonCode());
        item.setReviewerId(row.getReviewerId());
        item.setReviewedAt(row.getReviewedTime());
        item.setReusedFromCaseId(row.getReusedFromCaseId());
        return item;
    }

    /** 一项检查及其模板属性。 */
    static ClientReviewCaseResponse.Check check(ClientReviewFamily family, DueDiligenceCase owner, CaseCheck row) {
        ProgramCheck template = family.programOf(owner).getChecks().stream()
            .filter(t -> t.getProgramCheckId() == row.getProgramCheckId()).findFirst().orElseThrow();
        ClientReviewCaseResponse.Check item = new ClientReviewCaseResponse.Check();
        item.setCheckId(row.getCheckId());
        item.setCheckCode(template.getCheckCode());
        item.setCheckType(template.getCheckType());
        item.setCheckMode(row.getCheckMode());
        item.setBlocking(template.isBlocking());
        item.setRequired(template.isRequired());
        item.setStatus(row.getStatus());
        item.setOutcome(row.getOutcome());
        item.setReasonCode(row.getReasonCode());
        item.setEvidenceRef(row.getEvidenceRef());
        item.setNote(row.getNote());
        item.setReviewerId(row.getReviewerId());
        item.setReviewedAt(row.getReviewedTime());
        item.setValidUntil(row.getValidUntil());
        item.setReusedFromCaseId(row.getReusedFromCaseId());
        return item;
    }

    /** 一次筛查执行及其命中。 */
    static ClientReviewCaseResponse.ScreeningRunItem screening(ClientReviewFamily family, DueDiligenceCase owner, ScreeningRun run) {
        ProgramScreening template = family.programOf(owner).getScreenings().stream()
            .filter(t -> t.getProgramScreeningId() == run.getProgramScreeningId()).findFirst().orElseThrow();
        ClientReviewCaseResponse.ScreeningRunItem item = new ClientReviewCaseResponse.ScreeningRunItem();
        item.setRunId(run.getScreeningRunId());
        item.setScreeningType(template.getScreeningType());
        item.setProviderCode(run.getProviderCode());
        item.setStatus(run.getStatus());
        item.setTriggerSource(run.getTriggerSource());
        item.setStartedAt(run.getStartedTime());
        item.setCompletedAt(run.getCompletedTime());
        item.setHits(family.getHits().stream().filter(hit -> hit.getScreeningRunId() == run.getScreeningRunId())
            .map(hit -> hit(family, hit)).toList());
        return item;
    }

    /** 一条命中及其名单类型与处置。 */
    static ClientReviewCaseResponse.Hit hit(ClientReviewFamily family, ScreeningHit row) {
        ClientReviewCaseResponse.Hit item = new ClientReviewCaseResponse.Hit();
        item.setHitId(row.getHitId());
        item.setExternalHitRef(row.getExternalHitRef());
        item.setEntityType(row.getEntityType());
        item.setMatchType(row.getMatchType());
        item.setMatchScore(row.getMatchScore());
        item.setRiskLevel(row.getRiskLevel());
        item.setListTypes(family.listTypesOf(row.getHitId()));
        item.setStatus(row.getStatus());
        item.setResolutions(family.resolutionsOf(row.getHitId()).stream().map(ClientReviewCaseConverter::resolution).toList());
        return item;
    }

    /** 一次处置。 */
    static ClientReviewCaseResponse.Resolution resolution(ScreeningResolution row) {
        ClientReviewCaseResponse.Resolution item = new ClientReviewCaseResponse.Resolution();
        item.setResolutionVersion(row.getResolutionVersion());
        item.setResolution(row.getResolution());
        item.setReasonCode(row.getReasonCode());
        item.setNote(row.getNote());
        item.setReviewerId(row.getReviewerId());
        item.setResolvedAt(row.getResolvedTime());
        item.setOnBehalfOfPartnerId(row.getOnBehalfOfPartnerId());
        return item;
    }
}
