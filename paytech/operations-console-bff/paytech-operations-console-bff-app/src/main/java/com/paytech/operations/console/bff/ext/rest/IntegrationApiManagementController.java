package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformIntegrationApiManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 原子 API管理的浏览器入口：实时 PLT-I02 授权后原样转发 Platform，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class IntegrationApiManagementController {

    private final PlatformIntegrationApiManagementClient client;

    /** 分页查询接入方下的原子 API，按编码稳定排序。 */
    @GetMapping("/integrations/{integrationCode}/apis")
    public PagingResponseBody<IntegrationApiSummary> listIntegrationApis(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.listIntegrationApis(context, integrationCode, page, size, keyword);
    }

    /** 读取原子 API详情，含引用方。 */
    @GetMapping("/integrations/{integrationCode}/apis/{apiCode}")
    public IntegrationApiResponseBody getIntegrationApi(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("apiCode") String apiCode) {
        return client.getIntegrationApi(context, integrationCode, apiCode);
    }

    /** 创建原子 API。 */
    @PostMapping("/integrations/{integrationCode}/apis")
    public ExternalApiMutationResponseBody createIntegrationApi(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateIntegrationApiRequestBody request) {
        return client.createIntegrationApi(context, integrationCode, request);
    }

    /** 按当前版本整体修改原子 API。 */
    @PutMapping("/integrations/{integrationCode}/apis/{apiCode}")
    public ExternalApiMutationResponseBody updateIntegrationApi(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("apiCode") String apiCode,
        @Valid @RequestBody UpdateIntegrationApiRequestBody request) {
        return client.updateIntegrationApi(context, integrationCode, apiCode, request);
    }

    /** 按当前版本物理删除原子 API。 */
    @DeleteMapping("/integrations/{integrationCode}/apis/{apiCode}")
    public ExternalApiMutationResponseBody deleteIntegrationApi(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("apiCode") String apiCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteIntegrationApi(context, integrationCode, apiCode, request);
    }

    /** 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    @PostMapping("/integrations/{integrationCode}/apis/validate")
    public ValidationResponseBody validateIntegrationApi(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateIntegrationApiRequestBody request) {
        return client.validateIntegrationApi(context, integrationCode, request);
    }
}
