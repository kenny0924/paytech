package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.STABLE_CODE_PATTERN;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformGatewayConfigurationClient;
import com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/** Gateway Target/Route 页面入口；每个请求只委托一个 Platform 管理能力，不接收或转发 Idempotency-Key。 @author Codex */
@Validated
@RestController
@RequestMapping("/gateway-admin/v1")
@AllArgsConstructor
public class GatewayConfigurationController {
    private final PlatformGatewayConfigurationClient client;

    @GetMapping("/target-services")
    public PagingResponseBody<GatewayTargetSummaryResponseBody> targetServices(OperatorContext operator,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
        @Valid @ModelAttribute GatewayTargetQueryRequestBody query) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.targetServices(operator, paging.page(), paging.size(), query);
    }

    @GetMapping("/target-services/{targetServiceCode}")
    public GatewayTargetResponseBody targetService(OperatorContext operator,
        @PathVariable @Pattern(regexp = STABLE_CODE_PATTERN) String targetServiceCode) {
        return client.targetService(operator, targetServiceCode);
    }

    @PatchMapping("/target-services/{targetServiceCode}/status")
    public GatewayTargetStatusResponseBody changeTargetStatus(OperatorContext operator,
        @PathVariable @Pattern(regexp = STABLE_CODE_PATTERN) String targetServiceCode,
        @Valid @RequestBody ChangeGatewayTargetStatusRequestBody request) {
        return client.changeTargetStatus(operator, targetServiceCode, request);
    }

    @GetMapping("/routes")
    public PagingResponseBody<GatewayRouteSummaryResponseBody> routes(OperatorContext operator,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
        @Valid @ModelAttribute GatewayRouteQueryRequestBody query) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.routes(operator, paging.page(), paging.size(), query);
    }

    @GetMapping("/routes/{routeCode}")
    public GatewayRouteResponseBody route(OperatorContext operator,
        @PathVariable @Pattern(regexp = STABLE_CODE_PATTERN) String routeCode) {
        return client.route(operator, routeCode);
    }

    @PatchMapping("/routes/{routeCode}/status")
    public GatewayRouteStatusResponseBody changeRouteStatus(OperatorContext operator,
        @PathVariable @Pattern(regexp = STABLE_CODE_PATTERN) String routeCode,
        @Valid @RequestBody ChangeGatewayRouteStatusRequestBody request) {
        return client.changeRouteStatus(operator, routeCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PostMapping("/target-services")
    public ConfigurationMutationResponseBody createTarget(
        OperatorContext context,
        @Valid @RequestBody CreateGatewayTargetRequestBody request) {
        return client.createTarget(context, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PutMapping("/target-services/{targetServiceCode}")
    public ConfigurationMutationResponseBody updateTarget(
        OperatorContext context,
        @PathVariable("targetServiceCode") String targetServiceCode,
        @Valid @RequestBody UpdateGatewayTargetRequestBody request) {
        return client.updateTarget(context, targetServiceCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PostMapping("/routes")
    public ConfigurationMutationResponseBody createRoute(
        OperatorContext context,
        @Valid @RequestBody CreateGatewayRouteRequestBody request) {
        return client.createRoute(context, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PutMapping("/routes/{routeCode}")
    public ConfigurationMutationResponseBody updateRoute(
        OperatorContext context,
        @PathVariable("routeCode") String routeCode,
        @Valid @RequestBody UpdateGatewayRouteRequestBody request) {
        return client.updateRoute(context, routeCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/capabilities")
    public GatewayConfigurationCapabilitiesResponseBody capabilities(
        OperatorContext context) {
        return client.capabilities(context);
    }
}
