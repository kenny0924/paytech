package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.operations.console.bff.integration.PlatformExternalApiEditorClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.FlowEditorContextResponseBody;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 编辑器目录管理的浏览器入口：实时 PLT-I02 授权后原样转发 Platform，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class ExternalApiEditorController {

    private final PlatformExternalApiEditorClient client;

    /** 一次取得流程编辑器需要的标准 API 字段与本接入方全部原子 API 合同。 */
    @GetMapping("/integrations/{integrationCode}/flow-editor-context")
    public FlowEditorContextResponseBody getFlowEditorContext(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam("standardApiCode") String standardApiCode) {
        return client.getFlowEditorContext(context, integrationCode, standardApiCode);
    }

    /** 读取编辑器可选枚举、容量上限与注册函数签名。 */
    @GetMapping("/editor-capabilities")
    public EditorCapabilitiesResponseBody getEditorCapabilities(
        OperatorContext context) {
        return client.getEditorCapabilities(context);
    }
}
