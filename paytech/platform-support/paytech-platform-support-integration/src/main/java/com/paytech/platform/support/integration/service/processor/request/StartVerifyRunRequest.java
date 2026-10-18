package com.paytech.platform.support.integration.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发起沙箱验证运行的已规范化输入。 */
@Getter
@AllArgsConstructor
public class StartVerifyRunRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 路径中的用例编码。 */
    private final String caseCode;

    /** 运行原因，随运行快照保存。 */
    private final String reason;
}
