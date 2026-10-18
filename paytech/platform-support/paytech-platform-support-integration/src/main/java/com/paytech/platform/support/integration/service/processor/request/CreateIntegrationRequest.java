package com.paytech.platform.support.integration.service.processor.request;

import java.util.List;

import com.paytech.platform.support.integration.domain.EndpointDefinition;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建接入方的已规范化输入。 */
@Getter
@AllArgsConstructor
public class CreateIntegrationRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 接入方编码。 */
    private final String code;

    /** 名称，已去除首尾空白。 */
    private final String name;

    /** 说明，空白已规范为 null。 */
    private final String description;

    /** 出站地址，保持提交顺序。 */
    private final List<EndpointDefinition> endpoints;

    /** 创建原因。 */
    private final String reason;
}
