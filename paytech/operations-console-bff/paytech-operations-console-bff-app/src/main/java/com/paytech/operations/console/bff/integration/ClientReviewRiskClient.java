package com.paytech.operations.console.bff.integration;

import java.util.Set;

import com.paytech.compliance.api.request.ClientReviewCheckResultRequestBody;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.api.request.ClientReviewHitResolutionRequestBody;
import com.paytech.compliance.api.request.ClientReviewRecommendationRequestBody;
import com.paytech.compliance.api.request.ClientReviewReturnRequestBody;
import com.paytech.compliance.api.request.ClientReviewRiskRatingRequestBody;
import com.paytech.compliance.api.request.ClientReviewScreeningRunRequestBody;
import com.paytech.compliance.api.request.ClientReviewWaiverRequestBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewProgramListResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** compliance Client 入驻审核工作台：案件查询、材料 / 检查 / 筛查 / CRR / 推荐与结论；单次调用，不自动重试。 */
public interface ClientReviewRiskClient {
    /** O01 案件列表；responsiblePartnerId、jurisdictionCode 为可选过滤。 */
    PagingResponseBody<ClientReviewCaseResponseBody> list(OperatorContext operator, Integer page, Integer size, String status,
        String qualificationType, String applicationId, String responsiblePartnerId, String jurisdictionCode);

    /**
     * 入驻配置页：某辖区可见的尽调程序（该辖区程序与全局程序的全部版本）
     *
     * @param operator 当前操作人
     * @param jurisdictionCode 辖区代码
     * @return 程序版本，按辖区（全局在前）、程序代码、版本倒序
     */
    ClientReviewProgramListResponseBody programs(OperatorContext operator, String jurisdictionCode);

    /** O02 compliance 案件详情。 */
    ClientReviewCaseResponseBody detail(OperatorContext operator, String caseId);

    /** O10 读取案件后返回其全部证据文件 ID（由文件服务地址 /files/v1/{fileId} 解析）。 */
    Set<String> evidenceFileIds(OperatorContext operator, String caseId);

    /** O05 核验一份材料（拒绝带原因码）。 */
    ClientReviewCommandResponseBody reviewEvidence(OperatorContext operator, String caseId, String evidenceId,
        ClientReviewEvidenceReviewRequestBody request);

    /** CP-5 豁免一项材料要求。 */
    ClientReviewCommandResponseBody waiveRequirement(OperatorContext operator, String caseId, String requirementId,
        ClientReviewWaiverRequestBody request);

    /** CP-5 录入人工检查结论。 */
    ClientReviewCommandResponseBody recordCheckResult(OperatorContext operator, String caseId, String checkId,
        ClientReviewCheckResultRequestBody request);

    /** CP-5 重跑筛查。 */
    ClientReviewCommandResponseBody rerunScreenings(OperatorContext operator, String caseId, ClientReviewScreeningRunRequestBody request);

    /** CP-5 处置一条筛查命中。 */
    ClientReviewCommandResponseBody resolveScreeningHit(OperatorContext operator, String caseId, String hitId,
        ClientReviewHitResolutionRequestBody request);

    /** CP-5 手工 CRR。 */
    ClientReviewCommandResponseBody rateRisk(OperatorContext operator, String caseId, ClientReviewRiskRatingRequestBody request);

    /** CP-5 推荐结论。 */
    ClientReviewCommandResponseBody recommend(OperatorContext operator, String caseId, ClientReviewRecommendationRequestBody request);

    /** O07 退回：需求级错误 + 期限。 */
    ClientReviewCommandResponseBody returnCase(OperatorContext operator, String caseId, ClientReviewReturnRequestBody request);

    /** O08 通过。 */
    ClientReviewCommandResponseBody approve(OperatorContext operator, String caseId, ClientReviewDecisionRequestBody request);

    /** O09 最终拒绝。 */
    ClientReviewCommandResponseBody reject(OperatorContext operator, String caseId, ClientReviewDecisionRequestBody request);
}
