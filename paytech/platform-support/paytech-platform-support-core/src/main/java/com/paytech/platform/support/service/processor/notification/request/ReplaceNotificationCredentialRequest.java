package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 录入或更换发送账号密钥；toString 不输出密钥。 */
@Getter
@AllArgsConstructor
public class ReplaceNotificationCredentialRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 账号编码。 */
    private final String providerCode;

    /** Access Key ID。 */
    private final String accessKeyId;

    /** Secret Access Key。 */
    private final String secretAccessKey;

    /** 当前版本。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "ReplaceNotificationCredentialRequest(providerCode=" + providerCode + ")";
    }
}
