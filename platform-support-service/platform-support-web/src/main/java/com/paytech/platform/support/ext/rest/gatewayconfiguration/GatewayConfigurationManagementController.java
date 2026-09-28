package com.paytech.platform.support.ext.rest.gatewayconfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayTargetStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteSummaryResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetSummaryResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.GatewayConfigurationManagementProcessor;

import lombok.AllArgsConstructor;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.*;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;

/** Operations BFF 使用的 Gateway 当前配置 HTTP 适配器。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GatewayConfigurationManagementController implements GatewayConfigurationManagementApi {
    private final GatewayConfigurationManagementProcessor processor;

    @Audit(action = "OPERATIONS_GATEWAY_TARGET_LIST", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "TARGETS", recordSuccess = false)
    @Override
    public PagingResponseBody<GatewayTargetSummaryResponseBody> targetServices(String actor, String session,
        Integer page, Integer size, String targetServiceCode, String channel, String status) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return processor.targets(actor, session, paging.page(), paging.size(), targetServiceCode, channel, status);
    }

    @Audit(action = "OPERATIONS_GATEWAY_TARGET_DETAIL", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "INVALID_TARGET", recordSuccess = false)
    @Override
    public GatewayTargetResponseBody targetService(String actor, String session, String targetServiceCode) {
        return processor.target(actor, session, targetServiceCode);
    }

    @Audit(action = "OPERATIONS_GATEWAY_TARGET_STATUS_CHANGE", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "INVALID_TARGET")
    @Override
    public GatewayTargetStatusResponseBody changeTargetStatus(String actor, String session, String key,
        String targetServiceCode, ChangeGatewayTargetStatusRequestBody body) {
        return processor.changeTargetStatus(actor, session, key, targetServiceCode, body);
    }

    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_LIST", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "ROUTES", recordSuccess = false)
    @Override
    public PagingResponseBody<GatewayRouteSummaryResponseBody> routes(String actor, String session, Integer page,
        Integer size, String routeCode, String channel, String status, String targetServiceCode, String requiredScopeCode) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return processor.routes(actor, session, paging.page(), paging.size(), routeCode, channel, status,
            targetServiceCode, requiredScopeCode);
    }

    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_DETAIL", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "INVALID_ROUTE", recordSuccess = false)
    @Override
    public GatewayRouteResponseBody route(String actor, String session, String routeCode) {
        return processor.route(actor, session, routeCode);
    }

    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_STATUS_CHANGE", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "INVALID_ROUTE")
    @Override
    public GatewayRouteStatusResponseBody changeRouteStatus(String actor, String session, String key,
        String routeCode, ChangeGatewayRouteStatusRequestBody body) {
        return processor.changeRouteStatus(actor, session, key, routeCode, body);
    }


    /** 完整配置保存，仅转换协议并委托 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_TARGET_CREATE", resourceType = "GATEWAY_TARGET", fallbackResourceId = "NEW_TARGET")
    @Override
    public ConfigurationMutationResponseBody createTarget(String actor, String session, String key,
        CreateGatewayTargetRequestBody request) {
        return processor.createTarget(actor, session, key, request);
    }

    /** 完整配置保存，仅转换协议并委托 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_TARGET_UPDATE", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "NEW_TARGET")
    @Override
    public ConfigurationMutationResponseBody updateTarget(String actor, String session, String key,
        String targetServiceCode, UpdateGatewayTargetRequestBody request) {
        return processor.updateTarget(actor, session, key, targetServiceCode, request);
    }

    /** 完整配置保存，仅转换协议并委托 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_CREATE", resourceType = "GATEWAY_ROUTE", fallbackResourceId = "NEW_ROUTE")
    @Override
    public ConfigurationMutationResponseBody createRoute(String actor, String session, String key,
        CreateGatewayRouteRequestBody request) {
        return processor.createRoute(actor, session, key, request);
    }

    /** 完整配置保存，仅转换协议并委托 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_UPDATE", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "NEW_ROUTE")
    @Override
    public ConfigurationMutationResponseBody updateRoute(String actor, String session, String key,
        String routeCode, UpdateGatewayRouteRequestBody request) {
        return processor.updateRoute(actor, session, key, routeCode, request);
    }
    /** 配置选项来自同一 Platform 编译器。 */
    @Audit(action = "OPERATIONS_GATEWAY_CAPABILITIES", resourceType = "GATEWAY_TARGET",
        fallbackResourceId = "CAPABILITIES", recordSuccess = false)
    @Override
    public GatewayConfigurationCapabilitiesResponseBody capabilities(String actor, String session) {
        return processor.capabilities(actor, session);
    }
}
