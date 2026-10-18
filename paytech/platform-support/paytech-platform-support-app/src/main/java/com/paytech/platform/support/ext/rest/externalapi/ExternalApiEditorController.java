package com.paytech.platform.support.ext.rest.externalapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.externalapi.ExternalApiEditorApi;
import com.paytech.platform.support.api.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.platform.support.api.externalapi.response.FlowEditorContextResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.GetEditorCapabilitiesProcessor;
import com.paytech.platform.support.integration.service.processor.GetFlowEditorContextProcessor;

import lombok.AllArgsConstructor;

/** 外部 API 配置编辑器目录的协议入口，查询委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ExternalApiEditorController implements ExternalApiEditorApi {

    private final ExternalApiFlowConverter converter;
    private final ExternalApiManagementConverter common;
    private final GetFlowEditorContextProcessor contextProcessor;
    private final GetEditorCapabilitiesProcessor capabilitiesProcessor;

    /** 校验两个编码后委托编辑器上下文用例。 */
    @Audit(action = "OPERATIONS_FLOW_EDITOR_CONTEXT", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "INTEGRATION_IMPLEMENTATION_CATALOG", recordSuccess = false)
    @Override
    public FlowEditorContextResponseBody getFlowEditorContext(String actor, String session, String integrationCode,
        String standardApiCode) {
        return converter.toEditorContext(contextProcessor.process(
            common.toChildGetRequest(actor, session, integrationCode, standardApiCode)));
    }

    /** 委托能力目录用例。 */
    @Audit(action = "OPERATIONS_EDITOR_CAPABILITIES", resourceType = "EXTERNAL_API",
        fallbackResourceId = "EXTERNAL_API_EDITOR", recordSuccess = false)
    @Override
    public EditorCapabilitiesResponseBody getEditorCapabilities(String actor, String session) {
        return converter.toCapabilities(capabilitiesProcessor.process(converter.toActorRequest(actor, session)));
    }
}
