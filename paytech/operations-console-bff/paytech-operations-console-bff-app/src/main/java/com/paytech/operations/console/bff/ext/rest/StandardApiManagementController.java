package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.StandardApiManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiSummary;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 标准 API管理的浏览器入口：实时 PLT-I02 授权后原样转发 channel-connector，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class StandardApiManagementController {

    private final StandardApiManagementClient client;

    /** 分页查询标准 API，按编码稳定排序。 */
    @GetMapping("/standard-apis")
    public PagingResponseBody<StandardApiSummary> standardApis(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.standardApis(page, size, keyword);
    }

    /** 读取标准 API 详情，含字段与实现方。 */
    @GetMapping("/standard-apis/{standardApiCode}")
    public StandardApiResponseBody standardApi(
        @PathVariable("standardApiCode") String standardApiCode) {
        return client.standardApi(standardApiCode);
    }

    /** 创建标准 API 及其字段。 */
    @PostMapping("/standard-apis")
    public ExternalApiMutationResponseBody createStandardApi(
        OperatorContext context,
        @Valid @RequestBody CreateStandardApiRequestBody request) {
        return client.createStandardApi(context, request);
    }

    /** 按当前版本整体修改名称、说明与字段。 */
    @PutMapping("/standard-apis/{standardApiCode}")
    public ExternalApiMutationResponseBody updateStandardApi(
        OperatorContext context,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody UpdateStandardApiRequestBody request) {
        return client.updateStandardApi(context, standardApiCode, request);
    }

    /** 按当前版本物理删除标准 API 及其字段。 */
    @DeleteMapping("/standard-apis/{standardApiCode}")
    public ExternalApiMutationResponseBody deleteStandardApi(
        OperatorContext context,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteStandardApi(context, standardApiCode, request);
    }
}
