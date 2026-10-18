package com.paytech.platform.support.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询 Gateway 配置编译器能力的内部请求。 */
@Getter
@AllArgsConstructor
public class GatewayConfigurationCapabilitiesRequest {

    /** 运营账号，来自 Gateway 已验证的 Actor Subject；会话与查询权限由能力查询核验。 */
    private final String actorId;

    /** 运营会话。 */
    private final String sessionId;
}
