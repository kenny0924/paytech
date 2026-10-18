package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 资源用例输入，不携带 HTTP 请求幂等身份。 */
@Getter
@AllArgsConstructor
public class ChangeConsoleResourceStatusRequest {
    /** 可信 Operations 操作者。 */
    private final String actor;

    /** 当前会话。 */
    private final String session;

    /** 规范化资源编码。 */
    private final String resourceCode;

    /** 目标资源状态。 */
    private final GatewayConfigurationStatus status;

    /** 调用方期望版本。 */
    private final int expectedVersion;

    /** 规范化操作原因。 */
    private final String reason;

}
