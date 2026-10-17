package com.paytech.platform.support.api.clientapicredential.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 创建或轮换结果；完整密钥只在本响应中出现一次，调用方不得缓存或记录。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientApiCredentialSecretResponseBody {

    /** 新凭证摘要。 */
    private ClientApiCredentialResponseBody credential;

    /** 完整密钥（前缀.密文），之后无法再次查看。 */
    private String secret;

    /** 只返回脱敏前缀，避免日志或调试输出泄露密钥。 */
    @Override
    public String toString() {
        return "ClientApiCredentialSecretResponseBody[redacted]";
    }
}
