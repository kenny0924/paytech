package com.paytech.platform.support.accesscontrol.domain.clientdeveloper;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;

/** 创建或轮换结果：新凭证、只出现一次的完整密钥与推导状态的时刻。 */
public record ClientApiCredentialResult(ClientApiCredential credential, String secret, Instant asOf) {
    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "ClientApiCredentialResult[REDACTED]";
    }
}
