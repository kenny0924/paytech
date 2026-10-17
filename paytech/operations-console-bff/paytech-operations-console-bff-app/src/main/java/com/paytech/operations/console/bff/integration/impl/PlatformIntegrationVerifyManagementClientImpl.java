package com.paytech.operations.console.bff.integration.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
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
import com.paytech.platform.support.api.externalapi.IntegrationVerifyManagementApi;

import lombok.AllArgsConstructor;

/**
 * 渠道沙箱验证管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class PlatformIntegrationVerifyManagementClientImpl implements PlatformIntegrationVerifyManagementClient {

    private final IntegrationVerifyManagementApi api;

    /** V01 列出验证账号。 */
    @Override
    public List<VerifyAccountSummary> listVerifyAccounts(
        OperatorContext context,
        String integrationCode) {
        return api.listVerifyAccounts(context.accountId(), context.sessionId(), integrationCode).stream().map(ExternalApiContractConverter::toBff).toList();
    }

    /** V02 读取验证账号详情。 */
    @Override
    public VerifyAccountResponseBody getVerifyAccount(
        OperatorContext context,
        String integrationCode,
        String accountCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getVerifyAccount(context.accountId(), context.sessionId(), integrationCode, accountCode));
        ClientApiContractConverter.sameIdentity(accountCode, mapped.getAccountCode());
        return mapped;
    }

    /** V03 创建验证账号。 */
    @Override
    public ExternalApiMutationResponseBody createVerifyAccount(
        OperatorContext context,
        String integrationCode,
        CreateVerifyAccountRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createVerifyAccount(context.accountId(), context.sessionId(), integrationCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + request.getAccountCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V04 按当前版本修改验证账号。 */
    @Override
    public ExternalApiMutationResponseBody updateVerifyAccount(
        OperatorContext context,
        String integrationCode,
        String accountCode,
        UpdateVerifyAccountRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateVerifyAccount(context.accountId(), context.sessionId(), integrationCode, accountCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + accountCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V05 按当前版本删除验证账号。 */
    @Override
    public ExternalApiMutationResponseBody deleteVerifyAccount(
        OperatorContext context,
        String integrationCode,
        String accountCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteVerifyAccount(context.accountId(), context.sessionId(), integrationCode, accountCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + accountCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V06 分页查询验证用例。 */
    @Override
    public PagingResponseBody<VerifyCaseSummary> listVerifyCases(
        OperatorContext context,
        String integrationCode,
        Integer page,
        Integer size,
        String keyword) {
        var result = api.listVerifyCases(context.accountId(), context.sessionId(), integrationCode, page, size, keyword);
        return ClientApiContractConverter.page(result, page, size, ExternalApiContractConverter::toBff, VerifyCaseSummary::getCaseCode);
    }

    /** V07 读取验证用例详情。 */
    @Override
    public VerifyCaseResponseBody getVerifyCase(
        OperatorContext context,
        String integrationCode,
        String caseCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getVerifyCase(context.accountId(), context.sessionId(), integrationCode, caseCode));
        ClientApiContractConverter.sameIdentity(caseCode, mapped.getCaseCode());
        return mapped;
    }

    /** V08 创建验证用例。 */
    @Override
    public ExternalApiMutationResponseBody createVerifyCase(
        OperatorContext context,
        String integrationCode,
        CreateVerifyCaseRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createVerifyCase(context.accountId(), context.sessionId(), integrationCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + request.getCaseCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V09 按当前版本修改验证用例。 */
    @Override
    public ExternalApiMutationResponseBody updateVerifyCase(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        UpdateVerifyCaseRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateVerifyCase(context.accountId(), context.sessionId(), integrationCode, caseCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + caseCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V10 按当前版本删除验证用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteVerifyCase(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteVerifyCase(context.accountId(), context.sessionId(), integrationCode, caseCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + caseCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V11 只校验验证用例，不保存。 */
    @Override
    public ValidationResponseBody validateVerifyCase(
        OperatorContext context,
        String integrationCode,
        ValidateVerifyCaseRequestBody request) {
        try {
            return ExternalApiContractConverter.toBff(api.validateVerifyCase(context.accountId(), context.sessionId(), integrationCode, ExternalApiContractConverter.toPlatform(request)));
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V12 发起沙箱验证运行。 */
    @Override
    public VerifyRunStartResponseBody startVerifyRun(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        StartVerifyRunRequestBody request) {
        try {
            return ExternalApiContractConverter.toBff(api.startVerifyRun(context.accountId(), context.sessionId(),
                integrationCode, caseCode, ExternalApiContractConverter.toPlatform(request)));
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** V13 分页查询验证运行。 */
    @Override
    public PagingResponseBody<VerifyRunSummary> listVerifyRuns(
        OperatorContext context,
        String integrationCode,
        String caseCode,
        Integer page,
        Integer size) {
        var result = api.listVerifyRuns(context.accountId(), context.sessionId(), integrationCode, caseCode, page, size);
        return ClientApiContractConverter.page(result, page, size, ExternalApiContractConverter::toBff, VerifyRunSummary::getRunCode);
    }

    /** V14 读取验证运行详情与证据。 */
    @Override
    public VerifyRunResponseBody getVerifyRun(
        OperatorContext context,
        String integrationCode,
        String runCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getVerifyRun(context.accountId(), context.sessionId(), integrationCode, runCode));
        ClientApiContractConverter.sameIdentity(runCode, mapped.getRunCode());
        return mapped;
    }
}
