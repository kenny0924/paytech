package com.paytech.foundation.internalauth.api;

import com.nimbusds.jose.jwk.ECKey;

/**
 * 校验内部调用 Token 的 ES256 公钥来源。
 *
 * @author Claude
 */
public interface InternalTokenKeySource {

    /**
     * 按 {@code kid} 查找公钥；实现可在未命中时刷新一次。
     *
     * @param keyId Token 头中的 {@code kid}
     * @return P-256 公钥；确认不存在时为 null
     * @throws InternalTokenUnavailableException 公钥来源不可用
     */
    ECKey findKey(String keyId);
}
