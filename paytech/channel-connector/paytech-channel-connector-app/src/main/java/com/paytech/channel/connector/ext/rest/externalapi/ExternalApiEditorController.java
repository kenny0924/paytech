package com.paytech.channel.connector.ext.rest.externalapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.externalapi.ExternalApiEditorApi;
import com.paytech.channel.connector.api.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.channel.connector.api.externalapi.response.FlowEditorContextResponseBody;
import com.paytech.channel.connector.protocol.service.processor.EditorCapabilitiesCatalog;
import com.paytech.channel.connector.protocol.service.processor.GetFlowEditorContextProcessor;

import lombok.AllArgsConstructor;

/** 外部 API 配置编辑器目录的协议入口，查询委托 protocol 模块的 Processor 与能力目录。 */
@RestController
@AllArgsConstructor
public class ExternalApiEditorController implements ExternalApiEditorApi {

    private final ExternalApiFlowConverter converter;
    private final EditorCapabilitiesCatalog capabilitiesCatalog;
    private final ExternalApiManagementConverter common;
    private final GetFlowEditorContextProcessor contextProcessor;

    /** 校验两个编码后委托编辑器上下文用例。 */
    @Override
    public FlowEditorContextResponseBody getFlowEditorContext(String integrationCode, String standardApiCode) {
        return converter.toEditorContext(contextProcessor.process(
            common.toChildGetRequest(integrationCode, standardApiCode)));
    }

    /** 读取能力目录。 */
    @Override
    public EditorCapabilitiesResponseBody getEditorCapabilities() {
        return converter.toCapabilities(capabilitiesCatalog.capabilities());
    }
}
