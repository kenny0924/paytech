package com.paytech.channel.connector.protocol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发起沙箱验证运行的已规范化输入。 */
@Getter
@AllArgsConstructor
public class StartVerifyRunRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 路径中的用例编码。 */
    private final String caseCode;

    /** 运行原因，随运行快照保存。 */
    private final String reason;
}
