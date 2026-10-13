package com.paytech.risk.ext.rest.compliance;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.paytech.risk.api.compliance.response.ClientReviewCaseResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewClassificationResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewEvidenceResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewRequirementResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewSubjectResponseBody;
import com.paytech.risk.compliance.service.processor.response.ClientReviewCaseResponse;
import com.paytech.risk.compliance.service.processor.response.ClientReviewCommandResponse;

/** Owner 审核结果到 Wire 的纯转换；ID 与版本一律十进制字符串。 */
final class ClientReviewWireConverter {
    /** UTC 六位微秒时间格式。 */
    static final DateTimeFormatter MICROSECOND_UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC);

    private ClientReviewWireConverter() {
    }

    /** 任务摘要或详情。 */
    static ClientReviewCaseResponseBody caseBody(ClientReviewCaseResponse source) {
        var body = new ClientReviewCaseResponseBody();
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setCaseNo(source.getCaseNo());
        body.setApplicationId(Long.toString(source.getApplicationId()));
        body.setClientId(Long.toString(source.getClientId()));
        body.setSourceSubmissionId(source.getSourceSubmissionId());
        body.setQualificationType(source.getQualificationType());
        body.setStatus(source.getStatus());
        body.setVersion(Long.toString(source.getVersion()));
        body.setSubmittedAt(time(source.getSubmittedAt()));
        body.setDecidedAt(time(source.getDecidedAt()));
        body.setPublicNote(source.getPublicNote());
        body.setReturnSection(source.getReturnSection());
        body.setSubjects(source.getSubjects().stream().map(ClientReviewWireConverter::subject).toList());
        body.setClassifications(source.getClassifications().stream().map(ClientReviewWireConverter::classification).toList());
        body.setApprovalBlockers(source.getApprovalBlockers());
        return body;
    }

    /** 命令结果；本地同步完成。 */
    static ClientReviewCommandResponseBody command(ClientReviewCommandResponse source) {
        var body = new ClientReviewCommandResponseBody();
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setStatus(source.getStatus());
        body.setAccepted(true);
        body.setProcessing(false);
        return body;
    }

    /** 主体与材料。 */
    static ClientReviewSubjectResponseBody subject(ClientReviewCaseResponse.Subject source) {
        var body = new ClientReviewSubjectResponseBody();
        body.setCaseId(Long.toString(source.getCaseId()));
        body.setCaseStatus(source.getCaseStatus());
        body.setPartyId(Long.toString(source.getPartyId()));
        body.setProfileId(Long.toString(source.getProfileId()));
        body.setRoleType(source.getRoleType());
        body.setQualificationType(source.getQualificationType());
        body.setDependencyTypes(source.getDependencyTypes());
        body.setRequirements(source.getRequirements().stream().map(ClientReviewWireConverter::requirement).toList());
        return body;
    }

    /** 材料要求。 */
    static ClientReviewRequirementResponseBody requirement(ClientReviewCaseResponse.Requirement source) {
        var body = new ClientReviewRequirementResponseBody();
        body.setRequirementId(Long.toString(source.getRequirementId()));
        body.setRequirementCode(source.getRequirementCode());
        body.setDocumentType(source.getDocumentType());
        body.setRequired(source.isRequired());
        body.setStatus(source.getStatus());
        body.setEvidence(source.getEvidence().stream().map(ClientReviewWireConverter::evidence).toList());
        return body;
    }

    /** 证据。 */
    static ClientReviewEvidenceResponseBody evidence(ClientReviewCaseResponse.Evidence source) {
        var body = new ClientReviewEvidenceResponseBody();
        body.setEvidenceId(Long.toString(source.getEvidenceId()));
        body.setFileId(Long.toString(source.getFileId()));
        body.setStatus(source.getStatus());
        return body;
    }

    /** 分类。 */
    static ClientReviewClassificationResponseBody classification(ClientReviewCaseResponse.Classification source) {
        var body = new ClientReviewClassificationResponseBody();
        body.setIndustryCode(source.getIndustryCode());
        body.setMccCode(source.getMccCode());
        body.setPrimary(source.isPrimary());
        return body;
    }

    /** UTC 六位微秒，与其他 Risk 接口一致；空值保持为空。 */
    static String time(Instant value) {
        return value == null ? null : MICROSECOND_UTC.format(value);
    }
}
