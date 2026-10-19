package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.paytech.operations.console.bff.integration.PlatformConsoleResourceClient;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ConsoleResourceModels.*;

import jakarta.validation.Valid;
/** 页面入口沿用 PLT-I02、Origin 和可信身份拦截器。 */
@RestController
@Validated
@lombok.AllArgsConstructor
@RequestMapping("/console-admin/v1")
public class ConsoleResourceController {
    private final PlatformConsoleResourceClient client;

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/resources")
    public ConsoleResourcesResponseBody resources(
        OperatorContext context) {
        return client.resources(context);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/resources/{resourceCode}")
    public ConsoleResourceResponseBody resource(
        OperatorContext context,
        @PathVariable("resourceCode") String resourceCode) {
        return client.resource(context, resourceCode);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PostMapping("/resources")
    public ConfigurationMutationResponseBody createResource(
        OperatorContext context,
        @Valid @RequestBody CreateConsoleResourceRequestBody request) {
        return client.createResource(context, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PutMapping("/resources/{resourceCode}")
    public ConfigurationMutationResponseBody updateResource(
        OperatorContext context,
        @PathVariable("resourceCode") String resourceCode,
        @Valid @RequestBody UpdateConsoleResourceRequestBody request) {
        return client.updateResource(context, resourceCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PatchMapping("/resources/{resourceCode}/status")
    public ConfigurationMutationResponseBody changeResourceStatus(
        OperatorContext context,
        @PathVariable("resourceCode") String resourceCode,
        @Valid @RequestBody ChangeConsoleResourceStatusRequestBody request) {
        return client.changeResourceStatus(context, resourceCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/resource-capabilities")
    public ConsoleResourceCapabilitiesResponseBody capabilities(
        OperatorContext context) {
        return client.capabilities(context);
    }
}
