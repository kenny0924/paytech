package com.paytech.platform.support.ext.rest.gatewayconfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteDetailQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RoutePageQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetDetailQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetPageQuery;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayTargetStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayTargetRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayConfigurationCapabilitiesResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteSummaryResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetSummaryResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.ChangeGatewayRouteStatusProcessor;
import com.paytech.platform.support.service.processor.ChangeGatewayTargetStatusProcessor;
import com.paytech.platform.support.service.processor.CreateGatewayRouteProcessor;
import com.paytech.platform.support.service.processor.CreateGatewayTargetProcessor;
import com.paytech.platform.support.service.processor.GetGatewayConfigurationCapabilitiesProcessor;
import com.paytech.platform.support.service.processor.GetGatewayRouteProcessor;
import com.paytech.platform.support.service.processor.GetGatewayTargetProcessor;
import com.paytech.platform.support.service.processor.QueryGatewayRoutesProcessor;
import com.paytech.platform.support.service.processor.QueryGatewayTargetsProcessor;
import com.paytech.platform.support.service.processor.UpdateGatewayRouteProcessor;
import com.paytech.platform.support.service.processor.UpdateGatewayTargetProcessor;
import com.paytech.platform.support.service.processor.request.ChangeGatewayRouteStatusRequest;
import com.paytech.platform.support.service.processor.request.ChangeGatewayTargetStatusRequest;
import com.paytech.platform.support.service.processor.request.CreateGatewayRouteRequest;
import com.paytech.platform.support.service.processor.request.CreateGatewayTargetRequest;
import com.paytech.platform.support.service.processor.request.GatewayConfigurationCapabilitiesRequest;
import com.paytech.platform.support.service.processor.request.UpdateGatewayRouteRequest;
import com.paytech.platform.support.service.processor.request.UpdateGatewayTargetRequest;

import lombok.AllArgsConstructor;

/** Operations BFF 使用的 Gateway 当前配置 HTTP 适配器。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GatewayConfigurationManagementController implements GatewayConfigurationManagementApi {
    private final QueryGatewayTargetsProcessor queryTargetsProcessor;
    private final GetGatewayTargetProcessor getTargetProcessor;
    private final ChangeGatewayTargetStatusProcessor changeTargetStatusProcessor;
    private final QueryGatewayRoutesProcessor queryRoutesProcessor;
    private final GetGatewayRouteProcessor getRouteProcessor;
    private final ChangeGatewayRouteStatusProcessor changeRouteStatusProcessor;
    private final CreateGatewayTargetProcessor createTargetProcessor;
    private final UpdateGatewayTargetProcessor updateTargetProcessor;
    private final CreateGatewayRouteProcessor createRouteProcessor;
    private final UpdateGatewayRouteProcessor updateRouteProcessor;
    private final GetGatewayConfigurationCapabilitiesProcessor capabilitiesProcessor;

    @Audit(action = "OPERATIONS_GATEWAY_TARGET_LIST", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "TARGETS", recordSuccess = false)
    @Override
    public PagingResponseBody<GatewayTargetSummaryResponseBody> targetServices(String actor, String session,
        Integer page, Integer size, String targetServiceCode, String channel, String status) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return GatewayConfigurationManagementConverter.targetPage(queryTargetsProcessor.process(
            new TargetPageQuery(actor, session, paging.page(), paging.size(), targetServiceCode, channel, status)));
    }

    @Audit(action = "OPERATIONS_GATEWAY_TARGET_DETAIL", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "INVALID_TARGET", recordSuccess = false)
    @Override
    public GatewayTargetResponseBody targetService(String actor, String session, String targetServiceCode) {
        return GatewayConfigurationManagementConverter.target(getTargetProcessor.process(new TargetDetailQuery(actor, session, targetServiceCode)));
    }

    @Audit(action = "OPERATIONS_GATEWAY_TARGET_STATUS_CHANGE", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "INVALID_TARGET")
    @Override
    public GatewayTargetStatusResponseBody changeTargetStatus(String actor, String session,
        String targetServiceCode, ChangeGatewayTargetStatusRequestBody body) {
        return GatewayConfigurationManagementConverter.targetStatus(changeTargetStatusProcessor.process(
            new ChangeGatewayTargetStatusRequest(actor, session, targetServiceCode, body)));
    }

    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_LIST", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "ROUTES", recordSuccess = false)
    @Override
    public PagingResponseBody<GatewayRouteSummaryResponseBody> routes(String actor, String session, Integer page,
        Integer size, String routeCode, String channel, String status, String targetServiceCode, String requiredScopeCode) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return GatewayConfigurationManagementConverter.routePage(queryRoutesProcessor.process(new RoutePageQuery(actor, session, paging.page(),
            paging.size(), routeCode, channel, status, targetServiceCode, requiredScopeCode)));
    }

    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_DETAIL", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "INVALID_ROUTE", recordSuccess = false)
    @Override
    public GatewayRouteResponseBody route(String actor, String session, String routeCode) {
        return GatewayConfigurationManagementConverter.route(getRouteProcessor.process(new RouteDetailQuery(actor, session, routeCode)));
    }

    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_STATUS_CHANGE", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "INVALID_ROUTE")
    @Override
    public GatewayRouteStatusResponseBody changeRouteStatus(String actor, String session,
        String routeCode, ChangeGatewayRouteStatusRequestBody body) {
        return GatewayConfigurationManagementConverter.routeStatus(changeRouteStatusProcessor.process(
            new ChangeGatewayRouteStatusRequest(actor, session, routeCode, body)));
    }

    /** 完整配置保存，仅转换协议并委托对应 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_TARGET_CREATE", resourceType = "GATEWAY_TARGET", fallbackResourceId = "NEW_TARGET")
    @Override
    public ConfigurationMutationResponseBody createTarget(String actor, String session,
        CreateGatewayTargetRequestBody request) {
        return GatewayConfigurationManagementConverter.mutation(createTargetProcessor.process(new CreateGatewayTargetRequest(actor, session, request)));
    }

    /** 完整配置保存，仅转换协议并委托对应 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_TARGET_UPDATE", resourceType = "GATEWAY_TARGET",
        resourceIdParameter = "targetServiceCode", fallbackResourceId = "NEW_TARGET")
    @Override
    public ConfigurationMutationResponseBody updateTarget(String actor, String session,
        String targetServiceCode, UpdateGatewayTargetRequestBody request) {
        return GatewayConfigurationManagementConverter.mutation(updateTargetProcessor.process(
            new UpdateGatewayTargetRequest(actor, session, targetServiceCode, request)));
    }

    /** 完整配置保存，仅转换协议并委托对应 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_CREATE", resourceType = "GATEWAY_ROUTE", fallbackResourceId = "NEW_ROUTE")
    @Override
    public ConfigurationMutationResponseBody createRoute(String actor, String session,
        CreateGatewayRouteRequestBody request) {
        return GatewayConfigurationManagementConverter.mutation(createRouteProcessor.process(new CreateGatewayRouteRequest(actor, session, request)));
    }

    /** 完整配置保存，仅转换协议并委托对应 Processor。 */
    @Audit(action = "OPERATIONS_GATEWAY_ROUTE_UPDATE", resourceType = "GATEWAY_ROUTE",
        resourceIdParameter = "routeCode", fallbackResourceId = "NEW_ROUTE")
    @Override
    public ConfigurationMutationResponseBody updateRoute(String actor, String session,
        String routeCode, UpdateGatewayRouteRequestBody request) {
        return GatewayConfigurationManagementConverter.mutation(updateRouteProcessor.process(new UpdateGatewayRouteRequest(actor, session, routeCode, request)));
    }
    /** 配置选项来自同一 Platform 编译器。 */
    @Audit(action = "OPERATIONS_GATEWAY_CAPABILITIES", resourceType = "GATEWAY_TARGET",
        fallbackResourceId = "CAPABILITIES", recordSuccess = false)
    @Override
    public GatewayConfigurationCapabilitiesResponseBody capabilities(String actor, String session) {
        return GatewayConfigurationManagementConverter.capabilities(capabilitiesProcessor.process(new GatewayConfigurationCapabilitiesRequest(actor, session)));
    }
}
