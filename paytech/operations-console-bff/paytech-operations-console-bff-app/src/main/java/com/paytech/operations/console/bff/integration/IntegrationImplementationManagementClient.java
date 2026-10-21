package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationListResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;

/** 渠道标准 API 实现管理的 channel-connector 转发端口：只声明 BFF 本地模型；写命令传会话操作者 Account ID，查询与静态校验不传身份。 */
public interface IntegrationImplementationManagementClient {

    /** 查询接入方的全部标准 API 实现，按标准 API 编码排序，不分页。 */
    ImplementationListResponseBody listImplementations(
        String integrationCode);

    /** 读取一个标准 API 实现的流程定义。 */
    ImplementationResponseBody getImplementation(
        String integrationCode,
        String standardApiCode);

    /** 创建标准 API 实现。 */
    ExternalApiMutationResponseBody createImplementation(
        OperatorContext context,
        String integrationCode,
        CreateImplementationRequestBody request);

    /** 按当前版本整体替换流程。 */
    ExternalApiMutationResponseBody updateImplementation(
        OperatorContext context,
        String integrationCode,
        String standardApiCode,
        UpdateImplementationRequestBody request);

    /** 按当前版本物理删除实现。 */
    ExternalApiMutationResponseBody deleteImplementation(
        OperatorContext context,
        String integrationCode,
        String standardApiCode,
        DeleteExternalApiConfigRequestBody request);

    /** 只做流程静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    ValidationResponseBody validateImplementation(
        String integrationCode,
        ValidateImplementationRequestBody request);
}
