package com.paytech.operations.console.bff.integration;
import com.paytech.operations.console.bff.service.model.ConsoleResourceModels.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
/** 配置页面调用 Platform 的窄本地端口。 */
public interface PlatformConsoleResourceClient {

    /** 转发单个已授权配置操作。 */
    ConsoleResourcesResponseBody resources(OperatorContext context);

    /** 转发单个已授权配置操作。 */
    ConsoleResourceResponseBody resource(OperatorContext context, String resourceCode);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody createResource(OperatorContext context, CreateConsoleResourceRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody updateResource(OperatorContext context, String resourceCode, UpdateConsoleResourceRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody changeResourceStatus(OperatorContext context, String resourceCode, ChangeConsoleResourceStatusRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConsoleResourceCapabilitiesResponseBody capabilities(OperatorContext context);
}
