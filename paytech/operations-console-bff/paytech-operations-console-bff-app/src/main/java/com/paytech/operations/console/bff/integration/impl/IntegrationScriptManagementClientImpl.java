package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
import com.paytech.operations.console.bff.integration.IntegrationScriptManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationScriptManagementApi;

import lombok.AllArgsConstructor;

/**
 * 公共脚本管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class IntegrationScriptManagementClientImpl implements IntegrationScriptManagementClient {

    private final IntegrationScriptManagementApi api;

    /** 分页查询接入方下的公共脚本，按编码稳定排序。 */
    @Override
    public PagingResponseBody<IntegrationScriptSummary> listIntegrationScripts(
        String integrationCode,
        Integer page,
        Integer size,
        String keyword,
        String executionStage) {
        var result = api.listIntegrationScripts(integrationCode, page, size, keyword, executionStage);
        return ClientApiContractConverter.page(result, page, size, ExternalApiContractConverter::toBff, IntegrationScriptSummary::getScriptCode);
    }

    /** 读取公共脚本详情，含引用方。 */
    @Override
    public IntegrationScriptResponseBody getIntegrationScript(
        String integrationCode,
        String scriptCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getIntegrationScript(integrationCode, scriptCode));
        ClientApiContractConverter.sameIdentity(scriptCode, mapped.getScriptCode());
        return mapped;
    }

    /** 创建公共脚本。 */
    @Override
    public ExternalApiMutationResponseBody createIntegrationScript(
        OperatorContext context,
        String integrationCode,
        CreateIntegrationScriptRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createIntegrationScript(context.accountId(), integrationCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + request.getScriptCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本整体修改公共脚本。 */
    @Override
    public ExternalApiMutationResponseBody updateIntegrationScript(
        OperatorContext context,
        String integrationCode,
        String scriptCode,
        UpdateIntegrationScriptRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateIntegrationScript(context.accountId(), integrationCode, scriptCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + scriptCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本物理删除公共脚本。 */
    @Override
    public ExternalApiMutationResponseBody deleteIntegrationScript(
        OperatorContext context,
        String integrationCode,
        String scriptCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteIntegrationScript(context.accountId(), integrationCode, scriptCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + scriptCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    @Override
    public ValidationResponseBody validateIntegrationScript(
        String integrationCode,
        ValidateIntegrationScriptRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.validateIntegrationScript(integrationCode, ExternalApiContractConverter.toOwner(request)));
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }
}
