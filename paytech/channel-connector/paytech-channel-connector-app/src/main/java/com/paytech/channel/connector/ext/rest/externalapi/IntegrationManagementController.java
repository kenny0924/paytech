package com.paytech.channel.connector.ext.rest.externalapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationSummary;
import com.paytech.channel.connector.protocol.service.processor.CreateIntegrationProcessor;
import com.paytech.channel.connector.protocol.service.processor.DeleteIntegrationProcessor;
import com.paytech.channel.connector.protocol.service.processor.GetIntegrationProcessor;
import com.paytech.channel.connector.protocol.service.processor.ListIntegrationsProcessor;
import com.paytech.channel.connector.protocol.service.processor.UpdateIntegrationProcessor;

import lombok.AllArgsConstructor;

/** 外部接入方管理的协议入口，规则和事务委托 protocol 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class IntegrationManagementController implements IntegrationManagementApi {

    private final GetIntegrationProcessor detailProcessor;
    private final ListIntegrationsProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateIntegrationProcessor createProcessor;
    private final UpdateIntegrationProcessor updateProcessor;
    private final DeleteIntegrationProcessor deleteProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Override
    public PagingResponseBody<IntegrationSummary> integrations(Integer page,
        Integer size, String keyword) {
        return converter.toIntegrationPage(listProcessor.process(
            converter.toListRequest(page, size, keyword)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Override
    public IntegrationResponseBody integration(String integrationCode) {
        return converter.toIntegration(detailProcessor.process(converter.toGetRequest(integrationCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createIntegration(String actor,
        CreateIntegrationRequestBody request) {
        return converter.toMutation(createProcessor.process(converter.toCreateRequest(actor, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateIntegration(String actor, String integrationCode,
        UpdateIntegrationRequestBody request) {
        return converter.toMutation(
            updateProcessor.process(converter.toUpdateRequest(actor, integrationCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteIntegration(String actor, String integrationCode,
        DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(
            deleteProcessor.process(converter.toDeleteRequest(actor, integrationCode, request)));
    }
}
