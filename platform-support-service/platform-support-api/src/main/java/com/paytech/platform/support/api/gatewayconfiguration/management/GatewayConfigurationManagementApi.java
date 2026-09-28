package com.paytech.platform.support.api.gatewayconfiguration.management;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.*;

import jakarta.validation.Valid;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;

/** Operations BFF 使用的 Gateway Target 和 Route 当前配置管理能力。 */
@HttpExchange("/gateway-admin/v1")
public interface GatewayConfigurationManagementApi {

    @GetExchange("/target-services")
    PagingResponseBody<GatewayTargetSummaryResponseBody> targetServices(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "targetServiceCode", required = false) String targetServiceCode,
        @RequestParam(value = "channel", required = false) String channel,
        @RequestParam(value = "status", required = false) String status);

    @GetExchange("/target-services/{targetServiceCode}")
    GatewayTargetResponseBody targetService(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("targetServiceCode") String targetServiceCode);

    @PatchExchange("/target-services/{targetServiceCode}/status")
    GatewayTargetStatusResponseBody changeTargetStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("targetServiceCode") String targetServiceCode,
        @Valid @RequestBody ChangeGatewayTargetStatusRequestBody request);

    @GetExchange("/routes")
    PagingResponseBody<GatewayRouteSummaryResponseBody> routes(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "routeCode", required = false) String routeCode,
        @RequestParam(value = "channel", required = false) String channel,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "targetServiceCode", required = false) String targetServiceCode,
        @RequestParam(value = "requiredScopeCode", required = false) String requiredScopeCode);

    @GetExchange("/routes/{routeCode}")
    GatewayRouteResponseBody route(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("routeCode") String routeCode);

    @PatchExchange("/routes/{routeCode}/status")
    GatewayRouteStatusResponseBody changeRouteStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestHeader("Idempotency-Key") String idempotencyKey, @PathVariable("routeCode") String routeCode,
        @Valid @RequestBody ChangeGatewayRouteStatusRequestBody request);
    /** 保存完整配置并返回当次稳定回执；不自动启用。 */
    @PostExchange("/target-services")
    ConfigurationMutationResponseBody createTarget(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody CreateGatewayTargetRequestBody request);
    /** 保存完整配置并返回当次稳定回执；不自动启用。 */
    @PutExchange("/target-services/{targetServiceCode}")
    ConfigurationMutationResponseBody updateTarget(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestHeader("Idempotency-Key") String key,
        @PathVariable("targetServiceCode") String targetServiceCode,
        @Valid @RequestBody UpdateGatewayTargetRequestBody request);
    /** 保存完整配置并返回当次稳定回执；不自动启用。 */
    @PostExchange("/routes")
    ConfigurationMutationResponseBody createRoute(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody CreateGatewayRouteRequestBody request);
    /** 保存完整配置并返回当次稳定回执；不自动启用。 */
    @PutExchange("/routes/{routeCode}")
    ConfigurationMutationResponseBody updateRoute(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestHeader("Idempotency-Key") String key,
        @PathVariable("routeCode") String routeCode,
        @Valid @RequestBody UpdateGatewayRouteRequestBody request);
    /** 已部署编译器支持的组合与限制。 */
    @GetExchange("/capabilities")
    GatewayConfigurationCapabilitiesResponseBody capabilities(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);
}
