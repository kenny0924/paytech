package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.operations.console.bff.integration.PlatformIntegrationImplementationManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationListResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 渠道标准 API 实现管理的浏览器入口：实时 PLT-I02 授权后原样转发 Platform，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class IntegrationImplementationManagementController {

    private final PlatformIntegrationImplementationManagementClient client;

    /** 查询接入方的全部标准 API 实现，按标准 API 编码排序，不分页。 */
    @GetMapping("/integrations/{integrationCode}/implementations")
    public ImplementationListResponseBody listImplementations(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode) {
        return client.listImplementations(context, integrationCode);
    }

    /** 读取一个标准 API 实现的流程定义。 */
    @GetMapping("/integrations/{integrationCode}/implementations/{standardApiCode}")
    public ImplementationResponseBody getImplementation(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("standardApiCode") String standardApiCode) {
        return client.getImplementation(context, integrationCode, standardApiCode);
    }

    /** 创建标准 API 实现。 */
    @PostMapping("/integrations/{integrationCode}/implementations")
    public ExternalApiMutationResponseBody createImplementation(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateImplementationRequestBody request) {
        return client.createImplementation(context, integrationCode, request);
    }

    /** 按当前版本整体替换流程。 */
    @PutMapping("/integrations/{integrationCode}/implementations/{standardApiCode}")
    public ExternalApiMutationResponseBody updateImplementation(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody UpdateImplementationRequestBody request) {
        return client.updateImplementation(context, integrationCode, standardApiCode, request);
    }

    /** 按当前版本物理删除实现。 */
    @DeleteMapping("/integrations/{integrationCode}/implementations/{standardApiCode}")
    public ExternalApiMutationResponseBody deleteImplementation(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteImplementation(context, integrationCode, standardApiCode, request);
    }

    /** 只做流程静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    @PostMapping("/integrations/{integrationCode}/implementations/validate")
    public ValidationResponseBody validateImplementation(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateImplementationRequestBody request) {
        return client.validateImplementation(context, integrationCode, request);
    }
}
