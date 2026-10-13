package com.paytech.platform.support.integration.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接入方在本环境的一个出站地址；序列化后保存在 t_integration.endpoints。 */
@Getter
@AllArgsConstructor
public class EndpointDefinition {

    /** 接入方内唯一的地址代码。 */
    private final String endpointCode;

    /** https 基础地址，可带路径前缀。 */
    private final String baseUrl;
}
