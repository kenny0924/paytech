package com.paytech.foundation.internalauth.api;

/**
 * 入站内部调用 Token 校验。
 *
 * @author Claude
 */
public interface InternalTokenVerifier {

    /**
     * 校验签名、签发方、目标服务、实例与有效期。
     *
     * @param compactToken JWS Compact Token
     * @return 已验证的调用方
     * @throws InternalTokenInvalidException Token 不可接受
     * @throws InternalTokenUnavailableException 无法取得校验公钥，失败关闭
     */
    CallerIdentity verify(String compactToken);
}
