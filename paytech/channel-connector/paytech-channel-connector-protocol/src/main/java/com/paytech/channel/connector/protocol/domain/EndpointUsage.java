package com.paytech.channel.connector.protocol.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 出站地址及引用它的原子 API。 */
@Getter
@AllArgsConstructor
public class EndpointUsage {

    /** 地址代码。 */
    private final String endpointCode;

    /** 基础地址。 */
    private final String baseUrl;

    /** 是否沙箱地址。 */
    private final boolean sandbox;

    /** 引用该地址的原子 API 编码，按编码排序。 */
    private final List<String> referencedApiCodes;
}
