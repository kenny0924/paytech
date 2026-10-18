package com.paytech.platform.support.service.processor.request;

import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 全量更新 Route 的内部请求；身份来自 Path，Channel 与状态取事务内当前记录。 */
@Getter
@AllArgsConstructor
public class UpdateGatewayRouteRequest {

    /** 运营账号，来自 Gateway 已验证的 Actor Subject；会话与权限在配置事务内核验。 */
    private final String actorId;

    /** 运营会话。 */
    private final String sessionId;

    /** Path 中的 Route Code。 */
    private final String routeCode;

    /** 已通过入口字段校验的完整更新请求体，含正版本。 */
    private final UpdateGatewayRouteRequestBody body;
}
