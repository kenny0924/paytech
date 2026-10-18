package com.paytech.platform.support.service.processor.request;

import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建 Route 的内部请求；新 Route 固定为 DISABLED。 */
@Getter
@AllArgsConstructor
public class CreateGatewayRouteRequest {

    /** 运营账号，来自 Gateway 已验证的 Actor Subject；会话与权限在配置事务内核验。 */
    private final String actorId;

    /** 运营会话。 */
    private final String sessionId;

    /** 已通过入口字段校验的完整创建请求体。 */
    private final CreateGatewayRouteRequestBody body;
}
