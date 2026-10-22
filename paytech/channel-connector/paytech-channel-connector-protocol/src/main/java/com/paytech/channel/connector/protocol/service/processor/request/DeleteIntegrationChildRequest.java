package com.paytech.channel.connector.protocol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按版本删除渠道下级配置的输入。 */
@Getter
@AllArgsConstructor
public class DeleteIntegrationChildRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的渠道编码。 */
    private final String integrationCode;

    /** 已校验格式的下级配置编码。 */
    private final String code;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 删除原因，已去除首尾空白。 */
    private final String reason;
}
