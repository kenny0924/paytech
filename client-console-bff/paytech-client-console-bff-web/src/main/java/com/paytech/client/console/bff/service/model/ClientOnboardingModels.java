package com.paytech.client.console.bff.service.model;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileResponseBody;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
import java.util.List;
import java.util.Map;

/** BFF 独占的页面聚合；不包含 Hash、存储坐标或 Risk 程序修订。 */
public final class ClientOnboardingModels {

    /** 仅作为响应容器。 */
    private ClientOnboardingModels() {}

    /** 当前名额与最近申请；工作区切换另由 Platform 授权。 */
    public record StartResponseBody(
        List<ApplicationSummary> applications,
        boolean hasMore,
        String activeApplicationId
    ) {}

    /** unavailable 不得解释为无需材料；材料按资格类型通用，不按国家区分。 */
    public record MaterialSubject(
        String subjectPartyId,
        String qualificationType,
        String requirementsStatus,
        List<RequirementBody> requirements
    ) {}

    /** 文件无法查询时仅保留 CCT 引用；file=null 且 fileStatus=UNAVAILABLE。 */
    public record Document(
        String documentId,
        String subjectPartyId,
        String requirementCode,
        String documentType,
        String fileId,
        boolean subjectInScope,
        String fileStatus,
        FileResponseBody file
    ) {}

    /**
     * Risk 最近一次审核的客户可见结果；只在 CCT 状态为 RETURNED/APPROVED/REJECTED 时读取
     * <li>不含内部理由、核验记录或决定人。</li>
     */
    public record ReviewResult(String outcome, String returnSection, String publicNote, String decidedAt) {}

    /**
     * 当前表单和材料；只读状态由 CCT 决定，不能据此推断可提交
     * <li>reviewStatus：NONE 不适用；AVAILABLE 已读取 review；UNAVAILABLE Risk 不可用或与 CCT 状态不一致，不伪造结果。</li>
     */
    public record ApplicationResponseBody(
        String applicationId,
        String applicationNo,
        String clientId,
        String clientType,
        String subjectPartyId,
        String status,
        String savedAt,
        boolean editable,
        Map<String, Map<String, String>> values,
        List<PersonResponseBody> people,
        List<RelationResponseBody> relations,
        List<Document> documents,
        List<MaterialSubject> materialSubjects,
        String reviewStatus,
        ReviewResult review
    ) {
        /** 表单和文件展示名不打印到日志。 */
        @Override
        public String toString() {
            return "ApplicationResponseBody[REDACTED]";
        }
    }

}
