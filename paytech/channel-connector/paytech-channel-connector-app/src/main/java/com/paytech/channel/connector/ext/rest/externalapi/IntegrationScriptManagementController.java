package com.paytech.channel.connector.ext.rest.externalapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationScriptManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationScriptSummary;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.protocol.service.processor.CreateIntegrationScriptProcessor;
import com.paytech.channel.connector.protocol.service.processor.DeleteIntegrationScriptProcessor;
import com.paytech.channel.connector.protocol.service.processor.GetIntegrationScriptProcessor;
import com.paytech.channel.connector.protocol.service.processor.ListIntegrationScriptsProcessor;
import com.paytech.channel.connector.protocol.service.processor.UpdateIntegrationScriptProcessor;
import com.paytech.channel.connector.protocol.service.processor.ValidateIntegrationScriptProcessor;

import lombok.AllArgsConstructor;

/** 公共脚本管理的协议入口，规则和事务委托 protocol 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class IntegrationScriptManagementController implements IntegrationScriptManagementApi {

    private final GetIntegrationScriptProcessor detailProcessor;
    private final ListIntegrationScriptsProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateIntegrationScriptProcessor createProcessor;
    private final UpdateIntegrationScriptProcessor updateProcessor;
    private final DeleteIntegrationScriptProcessor deleteProcessor;
    private final ValidateIntegrationScriptProcessor validateProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Override
    public PagingResponseBody<IntegrationScriptSummary> listIntegrationScripts(String integrationCode,
        Integer page, Integer size, String keyword, String executionStage) {
        return converter.toScriptPage(listProcessor.process(converter.toChildListRequest(integrationCode, page, size, keyword, executionStage)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Override
    public IntegrationScriptResponseBody getIntegrationScript(String integrationCode, String scriptCode) {
        return converter.toScript(detailProcessor.process(
            converter.toChildGetRequest(integrationCode, scriptCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createIntegrationScript(String actor, String integrationCode,
        CreateIntegrationScriptRequestBody request) {
        return converter.toMutation(createProcessor.process(
            converter.toCreateScriptRequest(actor, integrationCode, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateIntegrationScript(String actor, String integrationCode,
        String scriptCode, UpdateIntegrationScriptRequestBody request) {
        return converter.toMutation(updateProcessor.process(
            converter.toUpdateScriptRequest(actor, integrationCode, scriptCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteIntegrationScript(String actor, String integrationCode,
        String scriptCode, DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(deleteProcessor.process(
            converter.toChildDeleteRequest(actor, integrationCode, scriptCode, request)));
    }

    /** 转换请求后委托只校验用例，问题以列表返回。 */
    @Override
    public ValidationResponseBody validateIntegrationScript(String integrationCode,
        ValidateIntegrationScriptRequestBody request) {
        return converter.toValidation(validateProcessor.process(
            converter.toValidateScriptRequest(integrationCode, request)));
    }
}
