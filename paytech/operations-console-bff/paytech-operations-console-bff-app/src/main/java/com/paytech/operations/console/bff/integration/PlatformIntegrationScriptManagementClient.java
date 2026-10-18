package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;

/** 公共脚本管理的 Platform 转发端口：只声明 BFF 本地模型，沿用可信 Operations 身份。 */
public interface PlatformIntegrationScriptManagementClient {

    /** 分页查询接入方下的公共脚本，按编码稳定排序。 */
    PagingResponseBody<IntegrationScriptSummary> listIntegrationScripts(
        OperatorContext context,
        String integrationCode,
        Integer page,
        Integer size,
        String keyword,
        String executionStage);

    /** 读取公共脚本详情，含引用方。 */
    IntegrationScriptResponseBody getIntegrationScript(
        OperatorContext context,
        String integrationCode,
        String scriptCode);

    /** 创建公共脚本。 */
    ExternalApiMutationResponseBody createIntegrationScript(
        OperatorContext context,
        String integrationCode,
        CreateIntegrationScriptRequestBody request);

    /** 按当前版本整体修改公共脚本。 */
    ExternalApiMutationResponseBody updateIntegrationScript(
        OperatorContext context,
        String integrationCode,
        String scriptCode,
        UpdateIntegrationScriptRequestBody request);

    /** 按当前版本物理删除公共脚本。 */
    ExternalApiMutationResponseBody deleteIntegrationScript(
        OperatorContext context,
        String integrationCode,
        String scriptCode,
        DeleteExternalApiConfigRequestBody request);

    /** 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    ValidationResponseBody validateIntegrationScript(
        OperatorContext context,
        String integrationCode,
        ValidateIntegrationScriptRequestBody request);
}
