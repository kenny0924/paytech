package com.paytech.compliance.ext.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCheckResponseBody;
import com.paytech.compliance.api.response.ClientReviewClassificationResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewEvidenceResponseBody;
import com.paytech.compliance.api.response.ClientReviewHitResolutionResponseBody;
import com.paytech.compliance.api.response.ClientReviewRequirementErrorBody;
import com.paytech.compliance.api.response.ClientReviewRequirementResponseBody;
import com.paytech.compliance.api.response.ClientReviewResultResponseBody;
import com.paytech.compliance.api.response.ClientReviewRiskFactorResponseBody;
import com.paytech.compliance.api.response.ClientReviewScreeningHitResponseBody;
import com.paytech.compliance.api.response.ClientReviewScreeningRunResponseBody;
import com.paytech.compliance.api.response.ClientReviewSubjectResponseBody;
import com.paytech.compliance.api.response.ClientReviewTimelineItemBody;
import com.paytech.compliance.api.response.ProgramCheckBody;
import com.paytech.compliance.api.response.ProgramMaterialBody;
import com.paytech.compliance.api.response.ClientReviewProgramResponseBody;
import com.paytech.compliance.api.response.ProgramResolutionResponseBody;
import com.paytech.compliance.api.response.ProgramScreeningBody;
import com.paytech.compliance.service.processor.response.ClientReviewCaseResponse;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.compliance.service.processor.response.ClientReviewResultResponse;
import com.paytech.compliance.service.processor.response.ProgramResolutionResponse;

/** Owner 审核结果到 Wire 的纯转换；ID 与版本一律十进制字符串，时间为 UTC 六位微秒。 */
final class ClientReviewWireConverter {
    /** UTC 六位微秒时间格式。 */
    static final DateTimeFormatter MICROSECOND_UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC);

    private ClientReviewWireConverter() {
    }

    /** 案件摘要或详情。 */
    static ClientReviewCaseResponseBody caseBody(ClientReviewCaseResponse source) {
        var body = new ClientReviewCaseResponseBody();
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setCaseNo(source.getCaseNo());
        body.setCaseType(source.getCaseType());
        body.setApplicationId(id(source.getApplicationId()));
        body.setClientId(Long.toString(source.getClientId()));
        body.setSourceSubmissionId(source.getSourceSubmissionId());
        body.setQualificationType(source.getQualificationType());
        body.setJurisdictionCode(source.getJurisdictionCode());
        body.setProgramVersionId(Long.toString(source.getProgramVersionId()));
        body.setTier(source.getTier());
        body.setRoundNo(source.getRoundNo());
        body.setPreviousCaseId(id(source.getPreviousCaseId()));
        body.setResponsiblePartnerId(id(source.getResponsiblePartnerId()));
        body.setStatus(source.getStatus());
        body.setVersion(Long.toString(source.getVersion()));
        body.setSubmittedAt(time(source.getSubmittedAt()));
        body.setDecidedAt(time(source.getDecidedAt()));
        body.setReviewDueTime(time(source.getReviewDueTime()));
        body.setRiskRating(source.getRiskRating());
        body.setRecommendation(source.getRecommendation());
        body.setPublicNote(source.getPublicNote());
        body.setReturnSection(source.getReturnSection());
        body.setOperatedBy(source.getOperatedBy());
        body.setOnBehalfOfPartnerId(id(source.getOnBehalfOfPartnerId()));
        detail(source, body);
        return body;
    }

    /** 详情专有字段；列表时为空集合。 */
    static void detail(ClientReviewCaseResponse source, ClientReviewCaseResponseBody body) {
        body.setFourEyesRequired(source.getFourEyesRequired());
        body.setRiskRatingNote(source.getRiskRatingNote());
        body.setRiskRatedBy(id(source.getRiskRatedBy()));
        body.setRiskRatedAt(time(source.getRiskRatedAt()));
        body.setRecommendedBy(id(source.getRecommendedBy()));
        body.setRecommendedAt(time(source.getRecommendedAt()));
        body.setRecommendationNote(source.getRecommendationNote());
        body.setRiskFactors(source.getRiskFactors().stream().map(item -> {
            var factor = new ClientReviewRiskFactorResponseBody();
            factor.setFactorCode(item.getFactorCode());
            factor.setFactorValue(item.getFactorValue());
            factor.setSource(item.getSource());
            factor.setRecordedBy(id(item.getRecordedBy()));
            factor.setRecordedAt(time(item.getRecordedAt()));
            return factor;
        }).toList());
        body.setReturnErrors(errors(source.getReturnErrors()));
        body.setSubjects(source.getSubjects().stream().map(ClientReviewWireConverter::subject).toList());
        body.setClassifications(source.getClassifications().stream().map(item -> {
            var classification = new ClientReviewClassificationResponseBody();
            classification.setIndustryCode(item.getIndustryCode());
            classification.setMccCode(item.getMccCode());
            classification.setPrimary(item.isPrimary());
            return classification;
        }).toList());
        body.setTimeline(source.getTimeline().stream().map(ClientReviewWireConverter::timeline).toList());
        body.setApprovalBlockers(source.getApprovalBlockers());
    }

    /** 退回需求级错误。 */
    static List<ClientReviewRequirementErrorBody> errors(List<ClientReviewCaseResponse.ErrorItem> items) {
        return items.stream().map(item -> {
            var error = new ClientReviewRequirementErrorBody();
            error.setRequirementKey(item.getRequirementKey());
            error.setSubjectPartyId(id(item.getSubjectPartyId()));
            error.setCode(item.getCode());
            error.setReason(item.getReason());
            error.setRemediation(item.getRemediation());
            error.setDueTime(time(item.getDueTime()));
            return error;
        }).toList();
    }

    /** 时间线一项。 */
    static ClientReviewTimelineItemBody timeline(ClientReviewCaseResponse.TimelineItem item) {
        var body = new ClientReviewTimelineItemBody();
        body.setCaseId(Long.toString(item.getCaseId()));
        body.setFromStatus(item.getFromStatus());
        body.setToStatus(item.getToStatus());
        body.setActionType(item.getActionType());
        body.setReasonCode(item.getReasonCode());
        body.setActorId(id(item.getActorId()));
        body.setOperatedBy(item.getOperatedBy());
        body.setOnBehalfOfPartnerId(id(item.getOnBehalfOfPartnerId()));
        body.setOccurredAt(time(item.getOccurredAt()));
        return body;
    }

    /** 主体与材料、检查、筛查。 */
    static ClientReviewSubjectResponseBody subject(ClientReviewCaseResponse.Subject source) {
        var body = new ClientReviewSubjectResponseBody();
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setCaseStatus(source.getCaseStatus());
        body.setPartyId(Long.toString(source.getPartyId()));
        body.setProfileId(Long.toString(source.getProfileId()));
        body.setRoleType(source.getRoleType());
        body.setQualificationType(source.getQualificationType());
        body.setUboBasis(source.getUboBasis());
        body.setJobTitle(source.getJobTitle());
        body.setDependencyTypes(source.getDependencyTypes());
        body.setRequirements(source.getRequirements().stream().map(ClientReviewWireConverter::requirement).toList());
        body.setChecks(source.getChecks().stream().map(ClientReviewWireConverter::check).toList());
        body.setScreenings(source.getScreenings().stream().map(ClientReviewWireConverter::screening).toList());
        return body;
    }

    /** 材料要求与证据。 */
    static ClientReviewRequirementResponseBody requirement(ClientReviewCaseResponse.Requirement source) {
        var body = new ClientReviewRequirementResponseBody();
        body.setRequirementId(Long.toString(source.getRequirementId()));
        body.setRequirementCode(source.getRequirementCode());
        body.setDocumentTypes(source.getDocumentTypes());
        body.setRequired(source.isRequired());
        body.setWaivable(source.isWaivable());
        body.setMaxAgeDays(source.getMaxAgeDays());
        body.setStatus(source.getStatus());
        body.setWaivedBy(id(source.getWaivedBy()));
        body.setWaivedReasonCode(source.getWaivedReasonCode());
        body.setWaivedAt(time(source.getWaivedAt()));
        body.setReusedFromCaseId(id(source.getReusedFromCaseId()));
        body.setEvidence(source.getEvidence().stream().map(item -> {
            var evidence = new ClientReviewEvidenceResponseBody();
            evidence.setEvidenceId(Long.toString(item.getEvidenceId()));
            evidence.setFileAddress(item.getFileAddress());
            evidence.setContentHash(item.getContentHash());
            evidence.setDocumentType(item.getDocumentType());
            evidence.setIssueDate(date(item.getIssueDate()));
            evidence.setExpiryDate(date(item.getExpiryDate()));
            evidence.setStatus(item.getStatus());
            evidence.setRejectReasonCode(item.getRejectReasonCode());
            evidence.setReviewerId(id(item.getReviewerId()));
            evidence.setReviewedAt(time(item.getReviewedAt()));
            evidence.setReusedFromCaseId(id(item.getReusedFromCaseId()));
            return evidence;
        }).toList());
        return body;
    }

    /** 检查。 */
    static ClientReviewCheckResponseBody check(ClientReviewCaseResponse.Check source) {
        var body = new ClientReviewCheckResponseBody();
        body.setCheckId(Long.toString(source.getCheckId()));
        body.setCheckCode(source.getCheckCode());
        body.setCheckType(source.getCheckType());
        body.setCheckMode(source.getCheckMode());
        body.setBlocking(source.isBlocking());
        body.setRequired(source.isRequired());
        body.setStatus(source.getStatus());
        body.setOutcome(source.getOutcome());
        body.setReasonCode(source.getReasonCode());
        body.setEvidenceRef(source.getEvidenceRef());
        body.setNote(source.getNote());
        body.setReviewerId(id(source.getReviewerId()));
        body.setReviewedAt(time(source.getReviewedAt()));
        body.setValidUntil(time(source.getValidUntil()));
        body.setReusedFromCaseId(id(source.getReusedFromCaseId()));
        return body;
    }

    /** 筛查执行与命中。 */
    static ClientReviewScreeningRunResponseBody screening(ClientReviewCaseResponse.ScreeningRunItem source) {
        var body = new ClientReviewScreeningRunResponseBody();
        body.setRunId(Long.toString(source.getRunId()));
        body.setScreeningType(source.getScreeningType());
        body.setProviderCode(source.getProviderCode());
        body.setStatus(source.getStatus());
        body.setTriggerSource(source.getTriggerSource());
        body.setStartedAt(time(source.getStartedAt()));
        body.setCompletedAt(time(source.getCompletedAt()));
        body.setHits(source.getHits().stream().map(ClientReviewWireConverter::hit).toList());
        return body;
    }

    /** 命中与处置。 */
    static ClientReviewScreeningHitResponseBody hit(ClientReviewCaseResponse.Hit source) {
        var body = new ClientReviewScreeningHitResponseBody();
        body.setHitId(Long.toString(source.getHitId()));
        body.setExternalHitRef(source.getExternalHitRef());
        body.setEntityType(source.getEntityType());
        body.setMatchType(source.getMatchType());
        body.setMatchScore(source.getMatchScore() == null ? null : source.getMatchScore().stripTrailingZeros().toPlainString());
        body.setRiskLevel(source.getRiskLevel());
        body.setListTypes(source.getListTypes());
        body.setStatus(source.getStatus());
        body.setResolutions(source.getResolutions().stream().map(item -> {
            var resolution = new ClientReviewHitResolutionResponseBody();
            resolution.setResolutionVersion(item.getResolutionVersion());
            resolution.setResolution(item.getResolution());
            resolution.setReasonCode(item.getReasonCode());
            resolution.setNote(item.getNote());
            resolution.setReviewerId(Long.toString(item.getReviewerId()));
            resolution.setResolvedAt(time(item.getResolvedAt()));
            resolution.setOnBehalfOfPartnerId(id(item.getOnBehalfOfPartnerId()));
            return resolution;
        }).toList());
        return body;
    }

    /** 命令结果；本地同步完成为 processing=false，重跑筛查为 true。 */
    static ClientReviewCommandResponseBody command(ClientReviewCommandResponse source) {
        var body = new ClientReviewCommandResponseBody();
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setStatus(source.getStatus());
        body.setAccepted(true);
        body.setProcessing(source.isProcessing());
        return body;
    }

    /** 客户可见结果。 */
    static ClientReviewResultResponseBody result(ClientReviewResultResponse source) {
        var body = new ClientReviewResultResponseBody();
        body.setApplicationId(Long.toString(source.getApplicationId()));
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setStatus(source.getStatus());
        body.setOutcome(source.getOutcome());
        body.setRoundNo(source.getRoundNo());
        body.setReturnSection(source.getReturnSection());
        body.setPublicNote(source.getPublicNote());
        body.setErrors(errors(source.getErrors()));
        body.setDueTime(time(source.getDueTime()));
        body.setDecidedAt(time(source.getDecidedAt()));
        return body;
    }

    /** CP-1 程序解析。 */
    static ProgramResolutionResponseBody program(ProgramResolutionResponse source) {
        return fillProgram(new ProgramResolutionResponseBody(), source);
    }

    /** O14 程序目录项：CP-1 字段加适用范围、状态与批准信息。 */
    static ClientReviewProgramResponseBody catalogProgram(ProgramResolutionResponse source) {
        ClientReviewProgramResponseBody body = fillProgram(new ClientReviewProgramResponseBody(), source);
        body.setPartyType(source.getPartyType());
        body.setRoleType(source.getRoleType());
        body.setProgramStatus(source.getProgramStatus());
        body.setVersionStatus(source.getVersionStatus());
        body.setApprovedBy(id(source.getApprovedBy()));
        body.setApprovedTime(time(source.getApprovedTime()));
        return body;
    }

    /** 填充 CP-1 程序字段与模板。 */
    static <T extends ProgramResolutionResponseBody> T fillProgram(T body, ProgramResolutionResponse source) {
        body.setProgramId(Long.toString(source.getProgramId()));
        body.setProgramCode(source.getProgramCode());
        body.setProgramVersionId(Long.toString(source.getProgramVersionId()));
        body.setVersionNo(source.getVersionNo());
        body.setQualificationType(source.getQualificationType());
        body.setJurisdictionCode(source.getJurisdictionCode());
        body.setTier(source.getTier());
        body.setOwnerPartnerId(id(source.getOwnerPartnerId()));
        body.setReviewIntervalMonths(source.getReviewIntervalMonths());
        body.setFourEyesRequired(source.isFourEyesRequired());
        body.setMaterials(source.getMaterials().stream().map(item -> {
            var material = new ProgramMaterialBody();
            material.setRequirementCode(item.getRequirementCode());
            material.setSubjectRole(item.getSubjectRole());
            material.setDocumentTypes(item.getDocumentTypes());
            material.setRequired(item.isRequired());
            material.setMaxAgeDays(item.getMaxAgeDays());
            material.setWaivable(item.isWaivable());
            return material;
        }).toList());
        body.setChecks(source.getChecks().stream().map(item -> {
            var check = new ProgramCheckBody();
            check.setCheckCode(item.getCheckCode());
            check.setCheckType(item.getCheckType());
            check.setCheckMode(item.getCheckMode());
            check.setSubjectRole(item.getSubjectRole());
            check.setBlocking(item.isBlocking());
            check.setValidityDays(item.getValidityDays());
            return check;
        }).toList());
        body.setScreenings(source.getScreenings().stream().map(item -> {
            var screening = new ProgramScreeningBody();
            screening.setScreeningType(item.getScreeningType());
            screening.setSubjectRole(item.getSubjectRole());
            screening.setRequired(item.isRequired());
            screening.setMonitoringRequired(item.isMonitoringRequired());
            return screening;
        }).toList());
        return body;
    }

    /** 可空 ID 的十进制字符串。 */
    static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    /** UTC 六位微秒；空值为 null。 */
    static String time(Instant value) {
        return value == null ? null : MICROSECOND_UTC.format(value);
    }

    /** YYYY-MM-DD；空值为 null。 */
    static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
