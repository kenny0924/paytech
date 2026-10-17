package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiSummary;

/** 标准 API管理的 Platform 转发端口：只声明 BFF 本地模型，沿用可信 Operations 身份。 */
public interface PlatformStandardApiManagementClient {

    /** 分页查询标准 API，按编码稳定排序。 */
    PagingResponseBody<StandardApiSummary> standardApis(
        OperatorContext context,
        Integer page,
        Integer size,
        String keyword);

    /** 读取标准 API 详情，含字段与实现方。 */
    StandardApiResponseBody standardApi(
        OperatorContext context,
        String standardApiCode);

    /** 创建标准 API 及其字段。 */
    ExternalApiMutationResponseBody createStandardApi(
        OperatorContext context,
        CreateStandardApiRequestBody request);

    /** 按当前版本整体修改名称、说明与字段。 */
    ExternalApiMutationResponseBody updateStandardApi(
        OperatorContext context,
        String standardApiCode,
        UpdateStandardApiRequestBody request);

    /** 按当前版本物理删除标准 API 及其字段。 */
    ExternalApiMutationResponseBody deleteStandardApi(
        OperatorContext context,
        String standardApiCode,
        DeleteExternalApiConfigRequestBody request);
}
