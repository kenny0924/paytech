package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成协议解析的凭证管理用例请求。 */
@Getter
@AllArgsConstructor
public class GetApiCredentialRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 已解析的正整数凭证标识。 */
    private final long credentialId;
}
