package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
import com.paytech.operations.console.bff.integration.PlatformExternalApiEditorClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.FlowEditorContextResponseBody;
import com.paytech.platform.support.api.externalapi.ExternalApiEditorApi;

import lombok.AllArgsConstructor;

/**
 * 编辑器目录管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class PlatformExternalApiEditorClientImpl implements PlatformExternalApiEditorClient {

    private final ExternalApiEditorApi api;

    /** 一次取得流程编辑器需要的标准 API 字段与本接入方全部原子 API 合同。 */
    @Override
    public FlowEditorContextResponseBody getFlowEditorContext(
        OperatorContext context,
        String integrationCode,
        String standardApiCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getFlowEditorContext(context.accountId(), context.sessionId(), integrationCode, standardApiCode));
        return mapped;
    }

    /** 读取编辑器可选枚举、容量上限与注册函数签名。 */
    @Override
    public EditorCapabilitiesResponseBody getEditorCapabilities(
        OperatorContext context) {
        var mapped = ExternalApiContractConverter.toBff(api.getEditorCapabilities(context.accountId(), context.sessionId()));
        return mapped;
    }
}
