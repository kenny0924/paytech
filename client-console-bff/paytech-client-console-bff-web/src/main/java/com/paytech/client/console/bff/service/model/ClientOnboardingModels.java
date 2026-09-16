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

    /** unavailable 与未填国家均不得解释为无需材料。 */
    public record MaterialSubject(
        String subjectPartyId,
        String qualificationType,
        String countryCode,
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

    /** 当前表单和材料；只读状态由 CCT 决定，不能据此推断可提交。 */
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
        List<MaterialSubject> materialSubjects
    ) {
        /** 表单和文件展示名不打印到日志。 */
        @Override
        public String toString() {
            return "ApplicationResponseBody[REDACTED]";
        }
    }

}
