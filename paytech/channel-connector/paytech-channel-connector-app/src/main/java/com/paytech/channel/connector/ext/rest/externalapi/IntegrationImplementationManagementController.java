package com.paytech.channel.connector.ext.rest.externalapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.externalapi.IntegrationImplementationManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateImplementationRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.ImplementationListResponseBody;
import com.paytech.channel.connector.api.externalapi.response.ImplementationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.protocol.service.processor.*;

import lombok.AllArgsConstructor;

/** 渠道标准 API 实现管理的协议入口，规则和事务委托 protocol 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class IntegrationImplementationManagementController implements IntegrationImplementationManagementApi {

    private final ExternalApiFlowConverter converter;
    private final ExternalApiManagementConverter common;
    private final GetImplementationProcessor detailProcessor;
    private final ListImplementationsProcessor listProcessor;
    private final CreateImplementationProcessor createProcessor;
    private final UpdateImplementationProcessor updateProcessor;
    private final DeleteImplementationProcessor deleteProcessor;
    private final ValidateImplementationProcessor validateProcessor;

    /** 校验渠道编码后委托列表用例。 */
    @Override
    public ImplementationListResponseBody listImplementations(String integrationCode) {
        return converter.toList(listProcessor.process(common.toGetRequest(integrationCode)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Override
    public ImplementationResponseBody getImplementation(String integrationCode,
        String standardApiCode) {
        return converter.toDetail(
            detailProcessor.process(common.toChildGetRequest(integrationCode, standardApiCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createImplementation(String actor, String integrationCode,
        CreateImplementationRequestBody request) {
        return common
            .toMutation(createProcessor.process(converter.toCreateRequest(actor, integrationCode, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateImplementation(String actor, String integrationCode,
        String standardApiCode, UpdateImplementationRequestBody request) {
        return common.toMutation(updateProcessor
            .process(converter.toUpdateRequest(actor, integrationCode, standardApiCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteImplementation(String actor, String integrationCode,
        String standardApiCode, DeleteExternalApiConfigRequestBody request) {
        return common.toMutation(deleteProcessor
            .process(common.toChildDeleteRequest(actor, integrationCode, standardApiCode, request)));
    }

    /** 转换请求后委托只校验用例，问题以列表返回。 */
    @Override
    public ValidationResponseBody validateImplementation(String integrationCode,
        ValidateImplementationRequestBody request) {
        return common.toValidation(
            validateProcessor.process(converter.toValidateRequest(integrationCode, request)));
    }
}
