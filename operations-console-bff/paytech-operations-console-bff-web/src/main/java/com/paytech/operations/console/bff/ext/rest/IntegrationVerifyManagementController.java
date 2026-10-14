package com.paytech.operations.console.bff.ext.rest;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformIntegrationVerifyManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateVerifyAccountRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateVerifyCaseRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateVerifyCaseRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyAccountResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyAccountSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyCaseResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyCaseSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyRunResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.VerifyRunSummary;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 渠道沙箱验证管理的浏览器入口：实时 PLT-I02 授权后原样转发 Platform，不接收或转发 Idempotency-Key。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/external-api-admin/v1")
public class IntegrationVerifyManagementController {

    private final PlatformIntegrationVerifyManagementClient client;

    /** V01 列出验证账号。 */
    @GetMapping("/integrations/{integrationCode}/verify-accounts")
    public List<VerifyAccountSummary> listVerifyAccounts(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode) {
        return client.listVerifyAccounts(context, integrationCode);
    }

    /** V02 读取验证账号详情。 */
    @GetMapping("/integrations/{integrationCode}/verify-accounts/{accountCode}")
    public VerifyAccountResponseBody getVerifyAccount(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("accountCode") String accountCode) {
        return client.getVerifyAccount(context, integrationCode, accountCode);
    }

    /** V03 创建验证账号。 */
    @PostMapping("/integrations/{integrationCode}/verify-accounts")
    public ExternalApiMutationResponseBody createVerifyAccount(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateVerifyAccountRequestBody request) {
        return client.createVerifyAccount(context, integrationCode, request);
    }

    /** V04 按当前版本修改验证账号。 */
    @PutMapping("/integrations/{integrationCode}/verify-accounts/{accountCode}")
    public ExternalApiMutationResponseBody updateVerifyAccount(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("accountCode") String accountCode,
        @Valid @RequestBody UpdateVerifyAccountRequestBody request) {
        return client.updateVerifyAccount(context, integrationCode, accountCode, request);
    }

    /** V05 按当前版本删除验证账号。 */
    @DeleteMapping("/integrations/{integrationCode}/verify-accounts/{accountCode}")
    public ExternalApiMutationResponseBody deleteVerifyAccount(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("accountCode") String accountCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteVerifyAccount(context, integrationCode, accountCode, request);
    }

    /** V06 分页查询验证用例。 */
    @GetMapping("/integrations/{integrationCode}/verify-cases")
    public PagingResponseBody<VerifyCaseSummary> listVerifyCases(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.listVerifyCases(context, integrationCode, page, size, keyword);
    }

    /** V07 读取验证用例详情。 */
    @GetMapping("/integrations/{integrationCode}/verify-cases/{caseCode}")
    public VerifyCaseResponseBody getVerifyCase(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode) {
        return client.getVerifyCase(context, integrationCode, caseCode);
    }

    /** V08 创建验证用例。 */
    @PostMapping("/integrations/{integrationCode}/verify-cases")
    public ExternalApiMutationResponseBody createVerifyCase(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateVerifyCaseRequestBody request) {
        return client.createVerifyCase(context, integrationCode, request);
    }

    /** V09 按当前版本修改验证用例。 */
    @PutMapping("/integrations/{integrationCode}/verify-cases/{caseCode}")
    public ExternalApiMutationResponseBody updateVerifyCase(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode,
        @Valid @RequestBody UpdateVerifyCaseRequestBody request) {
        return client.updateVerifyCase(context, integrationCode, caseCode, request);
    }

    /** V10 按当前版本删除验证用例。 */
    @DeleteMapping("/integrations/{integrationCode}/verify-cases/{caseCode}")
    public ExternalApiMutationResponseBody deleteVerifyCase(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request) {
        return client.deleteVerifyCase(context, integrationCode, caseCode, request);
    }

    /** V11 只校验验证用例，不保存。 */
    @PostMapping("/integrations/{integrationCode}/verify-cases/validate")
    public ValidationResponseBody validateVerifyCase(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateVerifyCaseRequestBody request) {
        return client.validateVerifyCase(context, integrationCode, request);
    }

    /** V12 发起沙箱验证运行。 */
    @PostMapping("/integrations/{integrationCode}/verify-cases/{caseCode}/runs")
    public VerifyRunStartResponseBody startVerifyRun(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode,
        @Valid @RequestBody StartVerifyRunRequestBody request) {
        return client.startVerifyRun(context, integrationCode, caseCode, request);
    }

    /** V13 分页查询验证运行。 */
    @GetMapping("/integrations/{integrationCode}/verify-runs")
    public PagingResponseBody<VerifyRunSummary> listVerifyRuns(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "caseCode", required = false) String caseCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listVerifyRuns(context, integrationCode, caseCode, page, size);
    }

    /** V14 读取验证运行详情与证据。 */
    @GetMapping("/integrations/{integrationCode}/verify-runs/{runCode}")
    public VerifyRunResponseBody getVerifyRun(
        OperatorContext context,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("runCode") String runCode) {
        return client.getVerifyRun(context, integrationCode, runCode);
    }
}
