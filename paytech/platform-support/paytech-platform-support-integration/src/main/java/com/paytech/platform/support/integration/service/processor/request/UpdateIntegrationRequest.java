package com.paytech.platform.support.integration.service.processor.request;

import java.util.List;

import com.paytech.platform.support.integration.domain.EndpointDefinition;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改接入方的已规范化输入。 */
@Getter
@AllArgsConstructor
public class UpdateIntegrationRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String code;

    /** 新名称。 */
    private final String name;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 新出站地址。 */
    private final List<EndpointDefinition> endpoints;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 修改原因。 */
    private final String reason;
}
