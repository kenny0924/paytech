package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.FlowEditorContextResponseBody;

/** 编辑器目录管理的 Platform 转发端口：只声明 BFF 本地模型，沿用可信 Operations 身份。 */
public interface PlatformExternalApiEditorClient {

    /** 一次取得流程编辑器需要的标准 API 字段与本接入方全部原子 API 合同。 */
    FlowEditorContextResponseBody getFlowEditorContext(
        OperatorContext context,
        String integrationCode,
        String standardApiCode);

    /** 读取编辑器可选枚举、容量上限与注册函数签名。 */
    EditorCapabilitiesResponseBody getEditorCapabilities(
        OperatorContext context);
}
