package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成协议解析的凭证管理用例请求。 */
@Getter
@AllArgsConstructor
public class RevokeApiCredentialRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 仅撤销指定凭证。 */
    private final long credentialId;

    /** 调用方读取的凭证版本。 */
    private final int expectedVersion;

    /** 已规范化的撤销原因。 */
    private final String reason;
}
