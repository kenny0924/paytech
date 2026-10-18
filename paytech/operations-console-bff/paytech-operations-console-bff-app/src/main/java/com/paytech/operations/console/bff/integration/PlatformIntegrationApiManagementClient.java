package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;

/** 原子 API管理的 Platform 转发端口：只声明 BFF 本地模型，沿用可信 Operations 身份。 */
public interface PlatformIntegrationApiManagementClient {

    /** 分页查询接入方下的原子 API，按编码稳定排序。 */
    PagingResponseBody<IntegrationApiSummary> listIntegrationApis(
        OperatorContext context,
        String integrationCode,
        Integer page,
        Integer size,
        String keyword);

    /** 读取原子 API详情，含引用方。 */
    IntegrationApiResponseBody getIntegrationApi(
        OperatorContext context,
        String integrationCode,
        String apiCode);

    /** 创建原子 API。 */
    ExternalApiMutationResponseBody createIntegrationApi(
        OperatorContext context,
        String integrationCode,
        CreateIntegrationApiRequestBody request);

    /** 按当前版本整体修改原子 API。 */
    ExternalApiMutationResponseBody updateIntegrationApi(
        OperatorContext context,
        String integrationCode,
        String apiCode,
        UpdateIntegrationApiRequestBody request);

    /** 按当前版本物理删除原子 API。 */
    ExternalApiMutationResponseBody deleteIntegrationApi(
        OperatorContext context,
        String integrationCode,
        String apiCode,
        DeleteExternalApiConfigRequestBody request);

    /** 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    ValidationResponseBody validateIntegrationApi(
        OperatorContext context,
        String integrationCode,
        ValidateIntegrationApiRequestBody request);
}
