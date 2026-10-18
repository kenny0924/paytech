package com.paytech.platform.support.accesscontrol.integration.signing;

import tools.jackson.databind.ObjectMapper;

/** 保留 Operations 本地适配器构造合同，私钥仍必须匹配 OPERATIONS 公钥命名空间。 */
public final class LocalOperationsTokenCryptography extends LocalHumanAuthenticationCryptography
    implements OperationsTokenCryptography {

    /** 保持既有 Operations 配置和测试入口不变。 */
    public LocalOperationsTokenCryptography(String encodedPrivateKey, String encodedHmacKey, int keyVersion,
        ObjectMapper json) {
        super(encodedPrivateKey, encodedHmacKey, keyVersion, json, "OPERATIONS");
    }
}
