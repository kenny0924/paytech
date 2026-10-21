package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationSummary;

/** 接入方管理的 channel-connector 转发端口：只声明 BFF 本地模型；写命令传会话操作者 Account ID，查询与静态校验不传身份。 */
public interface IntegrationManagementClient {

    /** 分页查询接入方，按编码稳定排序。 */
    PagingResponseBody<IntegrationSummary> integrations(
        Integer page,
        Integer size,
        String keyword);

    /** 读取接入方详情，含出站地址的引用方与下级对象数量。 */
    IntegrationResponseBody integration(
        String integrationCode);

    /** 创建接入方及其出站地址。 */
    ExternalApiMutationResponseBody createIntegration(
        OperatorContext context,
        CreateIntegrationRequestBody request);

    /** 按当前版本修改名称、说明与出站地址。 */
    ExternalApiMutationResponseBody updateIntegration(
        OperatorContext context,
        String integrationCode,
        UpdateIntegrationRequestBody request);

    /** 按当前版本物理删除接入方。 */
    ExternalApiMutationResponseBody deleteIntegration(
        OperatorContext context,
        String integrationCode,
        DeleteExternalApiConfigRequestBody request);
}
