package com.paytech.channel.connector.protocol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按编码读取单个外部 API 配置的输入。 */
@Getter
@AllArgsConstructor
public class GetExternalApiConfigRequest {

    /** 已校验格式的精确编码。 */
    private final String code;
}
