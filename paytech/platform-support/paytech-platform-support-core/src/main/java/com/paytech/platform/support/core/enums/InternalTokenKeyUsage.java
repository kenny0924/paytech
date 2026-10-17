package com.paytech.platform.support.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 内部调用 Token 签名密钥来源（{@code internal-token.issuer.key-usage}）。
 * <li>t_jwt_signing_key 的 INTERNAL 用途需先完成评审 Schema 变更（新增用途列），届时再增加对应取值；阶段 0 只有配置密钥。</li>
 */
public enum InternalTokenKeyUsage implements CodeEnum {
    /** 配置注入的 P-256 私钥签名；公钥与轮换中公钥由配置发布，prod Profile 禁止。 */
    STATIC("STATIC");

    private final String code;

    InternalTokenKeyUsage(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
