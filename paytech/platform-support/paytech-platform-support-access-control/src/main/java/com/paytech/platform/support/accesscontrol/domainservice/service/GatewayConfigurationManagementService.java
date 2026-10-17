package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.Page;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteDetailQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteDetailResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RoutePageQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteStatusCommand;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteStatusResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteSummaryResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetDetailQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetDetailResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetPageQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetStatusCommand;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetStatusResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetSummaryResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.*;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;

/** Gateway Target/Route 当前配置的授权查询与状态命令能力。 */
public interface GatewayConfigurationManagementService {

    Page<TargetSummaryResult> targets(TargetPageQuery query);

    TargetDetailResult target(TargetDetailQuery query);

    TargetStatusResult changeTargetStatus(TargetStatusCommand command);

    Page<RouteSummaryResult> routes(RoutePageQuery query);

    RouteDetailResult route(RouteDetailQuery query);

    RouteStatusResult changeRouteStatus(RouteStatusCommand command);

    /** 保存 Target，创建时停用，更新保留状态与固定身份。 */
    ConfigurationMutation saveTarget(String actor, String session, TargetWrite command);

    /** 保存 Route 完整聚合，任一校验或写入失败必须回滚。 */
    ConfigurationMutation saveRoute(String actor, String session, RouteWrite command);
    /** 当前编译器能力，按查询权限保护。 */
    com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCapabilities.Capabilities
        capabilities(String actor, String session);
}
