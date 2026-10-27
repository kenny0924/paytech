package com.paytech.operations.console.bff.integration;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ClassificationSetRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** CCT Client 入驻审核：申请资料读取与行业/MCC 分类；行业与 MCC 选项改由 Platform 参考数据提供；单次调用，不自动重试。 */
public interface ClientReviewCctClient {
    /** O02 申请资料。 */
    ClientDraftResponseBody application(OperatorContext operator, String caseId);

    /** O02 当前分类。 */
    ClassificationSetResponseBody classifications(OperatorContext operator, String caseId);

    /** O03 整体替换分类。 */
    ClassificationSetResponseBody replace(OperatorContext operator, String caseId, ClassificationSetRequestBody request);

    /** O04 确认分类。 */
    ClassificationSetResponseBody confirm(OperatorContext operator, String caseId);
}
