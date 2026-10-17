package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.DecisionRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.EvidenceReviewRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReturnRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.api.compliance.response.ClientReviewCaseResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;

/** Risk Client 入驻审核：任务查询、材料核验与结论；单次调用，不自动重试。 */
public interface ClientReviewRiskClient {
    /** O01 任务列表。 */
    PagingResponseBody<ClientReviewCaseResponseBody> list(OperatorContext operator, Integer page, Integer size, String status,
        String qualificationType, String applicationId);

    /** O02 Risk 任务详情。 */
    ClientReviewCaseResponseBody detail(OperatorContext operator, String caseId);

    /** O10 读取任务后返回其全部证据引用的文件标识。 */
    java.util.Set<String> evidenceFileIds(OperatorContext operator, String caseId);

    /** O05 核验一份材料。 */
    ClientReviewCommandResponseBody reviewEvidence(OperatorContext operator, String caseId, String evidenceId,
        EvidenceReviewRequestBody request);

    /** O07 退回修改。 */
    ClientReviewCommandResponseBody returnCase(OperatorContext operator, String caseId, ReturnRequestBody request);

    /** O08 通过。 */
    ClientReviewCommandResponseBody approve(OperatorContext operator, String caseId, DecisionRequestBody request);

    /** O09 最终拒绝。 */
    ClientReviewCommandResponseBody reject(OperatorContext operator, String caseId, DecisionRequestBody request);
}
