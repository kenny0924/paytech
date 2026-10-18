package com.paytech.platform.support.service.processor.request;

import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Route 启停的内部请求；状态、版本与原因在资源锁内规范化。 */
@Getter
@AllArgsConstructor
public class ChangeGatewayRouteStatusRequest {

    /** 运营账号，来自 Gateway 已验证的 Actor Subject；会话与权限在配置事务内核验。 */
    private final String actorId;

    /** 运营会话。 */
    private final String sessionId;

    /** Path 中的 Route Code。 */
    private final String routeCode;

    /** 启停请求体；为空时按缺少正版本拒绝。 */
    private final ChangeGatewayRouteStatusRequestBody body;
}
