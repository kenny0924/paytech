package com.paytech.channel.connector.ext.rest.externalapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationApiManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationApiResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationApiSummary;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.protocol.service.processor.CreateIntegrationApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.DeleteIntegrationApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.GetIntegrationApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.ListIntegrationApisProcessor;
import com.paytech.channel.connector.protocol.service.processor.UpdateIntegrationApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.ValidateIntegrationApiProcessor;

import lombok.AllArgsConstructor;

/** 原子 API管理的协议入口，规则和事务委托 protocol 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class IntegrationApiManagementController implements IntegrationApiManagementApi {

    private final GetIntegrationApiProcessor detailProcessor;
    private final ListIntegrationApisProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateIntegrationApiProcessor createProcessor;
    private final UpdateIntegrationApiProcessor updateProcessor;
    private final DeleteIntegrationApiProcessor deleteProcessor;
    private final ValidateIntegrationApiProcessor validateProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Override
    public PagingResponseBody<IntegrationApiSummary> listIntegrationApis(String integrationCode,
        Integer page, Integer size, String keyword) {
        return converter.toApiPage(listProcessor.process(converter.toChildListRequest(integrationCode, page, size, keyword, null)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Override
    public IntegrationApiResponseBody getIntegrationApi(String integrationCode, String apiCode) {
        return converter.toApi(detailProcessor.process(
            converter.toChildGetRequest(integrationCode, apiCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createIntegrationApi(String actor, String integrationCode,
        CreateIntegrationApiRequestBody request) {
        return converter.toMutation(createProcessor.process(
            converter.toCreateApiRequest(actor, integrationCode, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateIntegrationApi(String actor, String integrationCode,
        String apiCode, UpdateIntegrationApiRequestBody request) {
        return converter.toMutation(updateProcessor.process(
            converter.toUpdateApiRequest(actor, integrationCode, apiCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteIntegrationApi(String actor, String integrationCode,
        String apiCode, DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(deleteProcessor.process(
            converter.toChildDeleteRequest(actor, integrationCode, apiCode, request)));
    }

    /** 转换请求后委托只校验用例，问题以列表返回。 */
    @Override
    public ValidationResponseBody validateIntegrationApi(String integrationCode,
        ValidateIntegrationApiRequestBody request) {
        return converter.toValidation(validateProcessor.process(
            converter.toValidateApiRequest(integrationCode, request)));
    }
}
