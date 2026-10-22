package com.paytech.channel.connector.protocol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按编码读取渠道下级配置的输入。 */
@Getter
@AllArgsConstructor
public class GetIntegrationChildRequest {

    /** 路径中的渠道编码。 */
    private final String integrationCode;

    /** 已校验格式的下级配置编码。 */
    private final String code;
}
