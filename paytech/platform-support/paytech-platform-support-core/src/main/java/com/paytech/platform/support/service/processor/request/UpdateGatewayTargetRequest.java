package com.paytech.platform.support.service.processor.request;

import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 更新 Target 的内部请求；身份来自 Path，不能隐式转为创建。 */
@Getter
@AllArgsConstructor
public class UpdateGatewayTargetRequest {

    /** 运营账号，来自 Gateway 已验证的 Actor Subject；会话与权限在配置事务内核验。 */
    private final String actorId;

    /** 运营会话。 */
    private final String sessionId;

    /** Path 中的 Target Service Code。 */
    private final String targetServiceCode;

    /** 已通过入口字段校验的更新请求体，含正版本。 */
    private final UpdateGatewayTargetRequestBody body;
}
