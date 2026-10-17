package com.paytech.operations.console.bff.integration.impl;
import com.paytech.operations.console.bff.service.model.ConsoleResourceModels.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.integration.PlatformConsoleResourceClient;
import com.paytech.operations.console.bff.integration.ConfigurationContractConverter;
import com.paytech.platform.support.api.consolemanagement.ConsoleResourceManagementApi;
/** 单个 Platform API 的无状态转发适配。 */
@org.springframework.stereotype.Component
@lombok.AllArgsConstructor
public class PlatformConsoleResourceClientImpl implements PlatformConsoleResourceClient {
    private final ConsoleResourceManagementApi management;

    /** 转发参数与当前身份，请求幂等由 Gateway 处理。 */
    @Override
    public ConsoleResourcesResponseBody resources(OperatorContext context) {
        var result = management.resources(context.accountId(), context.sessionId());
        return ConfigurationContractConverter.toBff(result);
    }

    /** 转发参数与当前身份，请求幂等由 Gateway 处理。 */
    @Override
    public ConsoleResourceResponseBody resource(OperatorContext context, String resourceCode) {
        var result = management.resource(context.accountId(), context.sessionId(), resourceCode);
        return ConfigurationContractConverter.toBff(result);
    }

    /** 转发参数与当前身份，请求幂等由 Gateway 处理。 */
    @Override
    public ConfigurationMutationResponseBody createResource(OperatorContext context, CreateConsoleResourceRequestBody request) {
        try {
            var result = management.createResource(context.accountId(), context.sessionId(), ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 转发参数与当前身份，请求幂等由 Gateway 处理。 */
    @Override
    public ConfigurationMutationResponseBody updateResource(OperatorContext context, String resourceCode, UpdateConsoleResourceRequestBody request) {
        try {
            var result = management.updateResource(context.accountId(), context.sessionId(), resourceCode, ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 转发参数与当前身份，请求幂等由 Gateway 处理。 */
    @Override
    public ConfigurationMutationResponseBody changeResourceStatus(OperatorContext context, String resourceCode, ChangeConsoleResourceStatusRequestBody request) {
        try {
            var result = management.changeResourceStatus(context.accountId(), context.sessionId(), resourceCode, ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 转发参数与当前身份，请求幂等由 Gateway 处理。 */
    @Override
    public ConsoleResourceCapabilitiesResponseBody capabilities(OperatorContext context) {
        var result = management.capabilities(context.accountId(), context.sessionId());
        return ConfigurationContractConverter.toBff(result);
    }
}
