package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformIntegrationManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationSummary;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 接入方管理的浏览器入口：实时 PLT-I02 授权后原样转发 Platform，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class IntegrationManagementController {

    private final PlatformIntegrationManagementClient client;

    /** 分页查询接入方，按编码稳定排序。 */
    @GetMapping("/integrations")
    public PagingResponseBody<IntegrationSummary> integrations(
        OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.integrations(context, page, size, keyword);
    }

    /** 读取接入方详情，含出站地址的引用方与下级对象数量。 */
    @GetMapping("/integrations/{integrationCode}")
    public IntegrationResponseBody integration(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode) {
        return client.integration(context, integrationCode);
    }

    /** 创建接入方及其出站地址。 */
    @PostMapping("/integrations")
    public ExternalApiMutationResponseBody createIntegration(
        OperatorContext context,
        @Valid @RequestBody CreateIntegrationRequestBody request) {
        return client.createIntegration(context, request);
    }

    /** 按当前版本修改名称、说明与出站地址。 */
    @PutMapping("/integrations/{integrationCode}")
    public ExternalApiMutationResponseBody updateIntegration(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody UpdateIntegrationRequestBody request) {
        return client.updateIntegration(context, integrationCode, request);
    }

    /** 按当前版本物理删除接入方。 */
    @DeleteMapping("/integrations/{integrationCode}")
    public ExternalApiMutationResponseBody deleteIntegration(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteIntegration(context, integrationCode, request);
    }
}
