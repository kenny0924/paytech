package com.paytech.channel.connector.protocol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按版本删除外部 API 配置的输入。 */
@Getter
@AllArgsConstructor
public class DeleteExternalApiConfigRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 已校验格式的精确编码。 */
    private final String code;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 删除原因，已去除首尾空白。 */
    private final String reason;
}
