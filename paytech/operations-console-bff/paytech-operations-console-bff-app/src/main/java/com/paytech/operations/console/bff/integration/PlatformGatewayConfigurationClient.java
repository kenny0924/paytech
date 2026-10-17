package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** Platform Gateway 管理能力的 BFF 本地端口。 @author Codex */
public interface PlatformGatewayConfigurationClient {
    PagingResponseBody<GatewayTargetSummaryResponseBody> targetServices(OperatorContext context, int page, int size,
                                                             GatewayTargetQueryRequestBody query);

    GatewayTargetResponseBody targetService(OperatorContext context, String targetServiceCode);

    GatewayTargetStatusResponseBody changeTargetStatus(OperatorContext context,
                                                         String targetServiceCode,
                                                         ChangeGatewayTargetStatusRequestBody request);

    PagingResponseBody<GatewayRouteSummaryResponseBody> routes(OperatorContext context, int page, int size,
                                                   GatewayRouteQueryRequestBody query);

    GatewayRouteResponseBody route(OperatorContext context, String routeCode);

    GatewayRouteStatusResponseBody changeRouteStatus(OperatorContext context,
                                                       String routeCode, ChangeGatewayRouteStatusRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody createTarget(OperatorContext context, CreateGatewayTargetRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody updateTarget(OperatorContext context, String targetServiceCode, UpdateGatewayTargetRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody createRoute(OperatorContext context, CreateGatewayRouteRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody updateRoute(OperatorContext context, String routeCode, UpdateGatewayRouteRequestBody request);

    /** 转发单个已授权配置操作。 */
    GatewayConfigurationCapabilitiesResponseBody capabilities(OperatorContext context);
}
