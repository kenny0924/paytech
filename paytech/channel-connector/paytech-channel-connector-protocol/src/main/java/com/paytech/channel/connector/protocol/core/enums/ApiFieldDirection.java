package com.paytech.channel.connector.protocol.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** API 字段所属的 API 类型与请求或响应方向。 */
@Getter
@RequiredArgsConstructor
public enum ApiFieldDirection implements CodeEnum {

    /** 标准 API 请求。 */
    STANDARD_API_REQUEST("STANDARD_API_REQUEST"),

    /** 标准 API 响应。 */
    STANDARD_API_RESPONSE("STANDARD_API_RESPONSE"),

    /** 渠道原子 API 请求。 */
    INTEGRATION_API_REQUEST("INTEGRATION_API_REQUEST"),

    /** 渠道原子 API 响应。 */
    INTEGRATION_API_RESPONSE("INTEGRATION_API_RESPONSE");

    private final String code;
}
