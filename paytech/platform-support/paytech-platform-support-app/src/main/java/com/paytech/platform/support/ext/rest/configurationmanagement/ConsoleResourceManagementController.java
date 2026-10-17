package com.paytech.platform.support.ext.rest.configurationmanagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.*;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.consolemanagement.ConsoleResourceManagementApi;
import com.paytech.platform.support.api.consolemanagement.request.ChangeConsoleResourceStatusRequestBody;
import com.paytech.platform.support.api.consolemanagement.request.CreateConsoleResourceRequestBody;
import com.paytech.platform.support.api.consolemanagement.request.UpdateConsoleResourceRequestBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceCapabilitiesResponseBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceResponseBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourcesResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;

/** 配置管理的协议适配器，不承载事务或授权规则。 */
@RestController
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ConsoleResourceManagementController implements ConsoleResourceManagementApi {
    private final ConsoleResourceManagementConverter converter;
    private final ListConsoleResourcesProcessor listProcessor;
    private final GetConsoleResourceProcessor getProcessor;
    private final QueryConsoleResourceCapabilitiesProcessor capabilitiesProcessor;
    private final CreateConsoleResourceProcessor createProcessor;
    private final UpdateConsoleResourceProcessor updateProcessor;
    private final ChangeConsoleResourceStatusProcessor statusProcessor;

    /** 当前配置请求的显式转发，审计按真实业务结果记录。 */
    @Audit(action = "OPERATIONS_RESOURCES", resourceType = "CONSOLE_RESOURCE",
        fallbackResourceId = "CONSOLE_RESOURCE_CATALOG", recordSuccess = false)
    @Override
    public ConsoleResourcesResponseBody resources(String actor, String session) {
        return converter.resources(listProcessor.process(converter.resources(actor, session)));
    }

    /** 当前配置请求的显式转发，审计按真实业务结果记录。 */
    @Audit(action = "OPERATIONS_RESOURCE", resourceType = "CONSOLE_RESOURCE", resourceIdParameter = "resourceCode",
        fallbackResourceId = "CONSOLE_RESOURCE_CATALOG", recordSuccess = false)
    @Override
    public ConsoleResourceResponseBody resource(String actor, String session, String resourceCode) {
        return converter.resource(getProcessor.process(converter.resource(actor, session, resourceCode)));
    }

    /** 当前配置请求的显式转发，审计按真实业务结果记录。 */
    @Audit(action = "OPERATIONS_CREATE_RESOURCE", resourceType = "CONSOLE_RESOURCE",
        fallbackResourceId = "CONSOLE_RESOURCE_CATALOG", recordSuccess = true)
    @Override
    public ConfigurationMutationResponseBody createResource(String actor, String session,
        CreateConsoleResourceRequestBody request) {
        return converter.mutation(createProcessor.process(converter.create(actor, session, request)));
    }

    /** 当前配置请求的显式转发，审计按真实业务结果记录。 */
    @Audit(action = "OPERATIONS_UPDATE_RESOURCE", resourceType = "CONSOLE_RESOURCE", resourceIdParameter = "resourceCode",
        fallbackResourceId = "CONSOLE_RESOURCE_CATALOG", recordSuccess = true)
    @Override
    public ConfigurationMutationResponseBody updateResource(String actor, String session, String resourceCode,
        UpdateConsoleResourceRequestBody request) {
        return converter.mutation(updateProcessor.process(converter.update(actor, session, resourceCode, request)));
    }

    /** 当前配置请求的显式转发，审计按真实业务结果记录。 */
    @Audit(action = "OPERATIONS_CHANGE_RESOURCE_STATUS", resourceType = "CONSOLE_RESOURCE", resourceIdParameter = "resourceCode",
        fallbackResourceId = "CONSOLE_RESOURCE_CATALOG", recordSuccess = true)
    @Override
    public ConfigurationMutationResponseBody changeResourceStatus(String actor, String session, String resourceCode,
        ChangeConsoleResourceStatusRequestBody request) {
        return converter.mutation(statusProcessor.process(converter.status(actor, session, resourceCode, request)));
    }

    /** 当前配置请求的显式转发，审计按真实业务结果记录。 */
    @Audit(action = "OPERATIONS_CAPABILITIES", resourceType = "CONSOLE_RESOURCE",
        fallbackResourceId = "CONSOLE_RESOURCE_CATALOG", recordSuccess = false)
    @Override
    public ConsoleResourceCapabilitiesResponseBody capabilities(String actor, String session) {
        return converter.capabilities(capabilitiesProcessor.process(converter.capabilities(actor, session)));
    }
}
