package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.util.List;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayGuardRule;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteHeader;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteManagement;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** Gateway 配置管理查询条件及 Owner JAR 内的详情聚合。 */
public final class GatewayConfigurationManagementState {

    private GatewayConfigurationManagementState() {
    }

    public record TargetQuery(String code, GatewayRouteChannel channel, GatewayConfigurationStatus status) {
    }

    public record RouteQuery(String code, GatewayRouteChannel channel, GatewayConfigurationStatus status,
        String targetCode, String requiredScopeCode) {
    }

    public record RouteDetails(GatewayRouteManagement route, List<GatewayRouteHeader> headers,
        List<String> queryParameterNames, List<GatewayGuardRule> guardRules) {
        public RouteDetails {
            headers = List.copyOf(headers);
            queryParameterNames = List.copyOf(queryParameterNames);
            guardRules = List.copyOf(guardRules);
        }
    }
}
