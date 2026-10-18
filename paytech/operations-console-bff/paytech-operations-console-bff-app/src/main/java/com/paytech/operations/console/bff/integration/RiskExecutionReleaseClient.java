package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskrelease.CreateExecutionReleaseRequestBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseItemResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.StartShadowRunRequestBody;

/** Risk 执行发布的出站端口：列表、创建、详情、校验、影子运行与激活。 */
public interface RiskExecutionReleaseClient {
    /** 按决策点分页查询执行发布，按创建时间倒序。 */
    PagingResponseBody<ExecutionReleaseItemResponseBody> listExecutions(OperatorContext context, String pointCode,
        String status, int page, int size);

    /** 创建执行发布。 */
    ExecutionReleaseResponseBody createExecution(OperatorContext context, CreateExecutionReleaseRequestBody body);

    /** 查询执行发布详情。 */
    ExecutionReleaseResponseBody getExecution(OperatorContext context, String releaseId);

    /** 执行静态校验。 */
    ExecutionReleaseResponseBody validateExecution(OperatorContext context, String releaseId);

    /** 发起影子运行。 */
    ExecutionReleaseResponseBody startShadowRun(OperatorContext context, String releaseId, StartShadowRunRequestBody body);

    /** 激活执行发布。 */
    ExecutionReleaseResponseBody activateExecution(OperatorContext context, String releaseId);
}
