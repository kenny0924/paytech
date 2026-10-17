package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformIntegrationScriptManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 公共脚本管理的浏览器入口：实时 PLT-I02 授权后原样转发 Platform，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class IntegrationScriptManagementController {

    private final PlatformIntegrationScriptManagementClient client;

    /** 分页查询接入方下的公共脚本，按编码稳定排序。 */
    @GetMapping("/integrations/{integrationCode}/scripts")
    public PagingResponseBody<IntegrationScriptSummary> listIntegrationScripts(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "executionStage", required = false) String executionStage) {
        return client.listIntegrationScripts(context, integrationCode, page, size, keyword, executionStage);
    }

    /** 读取公共脚本详情，含引用方。 */
    @GetMapping("/integrations/{integrationCode}/scripts/{scriptCode}")
    public IntegrationScriptResponseBody getIntegrationScript(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("scriptCode") String scriptCode) {
        return client.getIntegrationScript(context, integrationCode, scriptCode);
    }

    /** 创建公共脚本。 */
    @PostMapping("/integrations/{integrationCode}/scripts")
    public ExternalApiMutationResponseBody createIntegrationScript(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateIntegrationScriptRequestBody request) {
        return client.createIntegrationScript(context, integrationCode, request);
    }

    /** 按当前版本整体修改公共脚本。 */
    @PutMapping("/integrations/{integrationCode}/scripts/{scriptCode}")
    public ExternalApiMutationResponseBody updateIntegrationScript(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("scriptCode") String scriptCode,
        @Valid @RequestBody UpdateIntegrationScriptRequestBody request) {
        return client.updateIntegrationScript(context, integrationCode, scriptCode, request);
    }

    /** 按当前版本物理删除公共脚本。 */
    @DeleteMapping("/integrations/{integrationCode}/scripts/{scriptCode}")
    public ExternalApiMutationResponseBody deleteIntegrationScript(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("scriptCode") String scriptCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteIntegrationScript(context, integrationCode, scriptCode, request);
    }

    /** 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    @PostMapping("/integrations/{integrationCode}/scripts/validate")
    public ValidationResponseBody validateIntegrationScript(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateIntegrationScriptRequestBody request) {
        return client.validateIntegrationScript(context, integrationCode, request);
    }
}
