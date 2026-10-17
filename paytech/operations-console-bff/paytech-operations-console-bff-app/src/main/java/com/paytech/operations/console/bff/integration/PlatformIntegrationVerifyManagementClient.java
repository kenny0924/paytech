package com.paytech.operations.console.bff.integration;

import java.util.List;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

/** 渠道沙箱验证管理的 Platform 转发端口：只声明 BFF 本地模型，沿用可信 Operations 身份。 */
public interface PlatformIntegrationVerifyManagementClient {

    /** V01 列出验证账号。 */
    List<VerifyAccountSummary> listVerifyAccounts(
        OperatorContext context,
        String integrationCode);

    /** V02 读取验证账号详情。 */
    VerifyAccountResponseBody getVerifyAccount(
        OperatorContext context,
        String integrationCode,
        String accountCode);

    /** V03 创建验证账号。 */
    ExternalApiMutationResponseBody createVerifyAccount(
        OperatorContext context,
        String integrationCode,
        CreateVerifyAccountRequestBody request);

    /** V04 按当前版本修改验证账号。 */
    ExternalApiMutationResponseBody updateVerifyAccount(
        OperatorContext context,
        String integrationCode,
        String accountCode,
        UpdateVerifyAccountRequestBody request);

    /** V05 按当前版本删除验证账号。 */
    ExternalApiMutationResponseBody deleteVerifyAccount(
        OperatorContext context,
        String integrationCode,
        String accountCode,
        DeleteExternalApiConfigRequestBody request);

    /** V06 分页查询验证用例。 */
    PagingResponseBody<VerifyCaseSummary> listVerifyCases(
        OperatorContext context,
        String integrationCode,
        Integer page,
        Integer size,
        String keyword);

    /** V07 读取验证用例详情。 */
    VerifyCaseResponseBody getVerifyCase(
        OperatorContext context,
        String integrationCode,
        String caseCode);

    /** V08 创建验证用例。 */
    ExternalApiMutationResponseBody createVerifyCase(
        OperatorContext context,
        String integrationCode,
        CreateVerifyCaseRequestBody request);

    /** V09 按当前版本修改验证用例。 */
    ExternalApiMutationResponseBody updateVerifyCase(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        UpdateVerifyCaseRequestBody request);

    /** V10 按当前版本删除验证用例。 */
    ExternalApiMutationResponseBody deleteVerifyCase(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        DeleteExternalApiConfigRequestBody request);

    /** V11 只校验验证用例，不保存。 */
    ValidationResponseBody validateVerifyCase(
        OperatorContext context,
        String integrationCode,
        ValidateVerifyCaseRequestBody request);

    /** V12 发起沙箱验证运行。 */
    VerifyRunStartResponseBody startVerifyRun(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        StartVerifyRunRequestBody request);

    /** V13 分页查询验证运行。 */
    PagingResponseBody<VerifyRunSummary> listVerifyRuns(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        Integer page,
        Integer size);

    /** V14 读取验证运行详情与证据。 */
    VerifyRunResponseBody getVerifyRun(
        OperatorContext context,
        String integrationCode,
        String runCode);
}
