package com.paytech.compliance.service.processor;

import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy;
import com.paytech.compliance.service.processor.response.ClientReviewCaseResponse;

/** 把模块内审核全貌转换为可跨模块的 Owner 结果；纯转换。 */
final class ClientReviewCaseConverter {
    private ClientReviewCaseConverter() {
    }

    /** 列表摘要。 */
    static ClientReviewCaseResponse summary(DueDiligenceCase row) {
        return new ClientReviewCaseResponse(row.getCaseId(), row.getCaseNo(), row.getSourceApplicationId(), row.getRoleId(),
            row.getSourceSubmissionId(), row.getQualificationType(), row.getStatus(), row.getVersion(), row.getSubmittedTime(),
            row.getDecisionTime(), row.getPublicNote(), row.getReturnSection(), List.of(), List.of(), List.of());
    }

    /** 详情。 */
    static ClientReviewCaseResponse detail(ClientReviewFamily family) {
        DueDiligenceCase main = family.getMainCase();
        return new ClientReviewCaseResponse(main.getCaseId(), main.getCaseNo(), main.getSourceApplicationId(), main.getRoleId(),
            main.getSourceSubmissionId(), main.getQualificationType(), main.getStatus(), main.getVersion(), main.getSubmittedTime(),
            main.getDecisionTime(), main.getPublicNote(), main.getReturnSection(),
            family.allCases().stream().map(row -> subject(family, row)).toList(),
            family.getClassifications().stream().map(c -> new ClientReviewCaseResponse.Classification(c.getIndustryCode(),
                c.getMccCode(), Integer.valueOf(1).equals(c.getPrimarySlot()))).toList(),
            ClientReviewApprovalPolicy.blockers(family));
    }

    /** 单个案件的主体与材料。 */
    static ClientReviewCaseResponse.Subject subject(ClientReviewFamily family, DueDiligenceCase row) {
        CaseSubject subject = family.getSubjects().stream().filter(s -> s.getCaseId() == row.getCaseId()).findFirst().orElseThrow();
        List<String> dependencyTypes = family.getDependencies().stream().filter(d -> d.getChildCaseId() == row.getCaseId())
            .map(d -> d.getDependencyType()).toList();
        var requirements = family.getRequirements().stream().filter(r -> r.getCaseId() == row.getCaseId())
            .map(r -> new ClientReviewCaseResponse.Requirement(r.getRequirementId(), r.getRequirementCode(), r.getDocumentType(),
                r.getRequiredFlag() == 1, r.getStatus(), family.getEvidence().stream().filter(e -> e.getRequirementId() == r.getRequirementId())
                    .map(e -> new ClientReviewCaseResponse.Evidence(e.getEvidenceId(), e.getFileId(), e.getStatus())).toList()))
            .toList();
        return new ClientReviewCaseResponse.Subject(row.getCaseId(), row.getStatus(), subject.getPartyId(), subject.getSourceProfileId(),
            row.getRoleType(), row.getQualificationType(), dependencyTypes, requirements);
    }
}
