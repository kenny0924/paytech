package com.paytech.platform.support.api.internalauth.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 内部调用 Token 的完整 JSON Web Key Set（RFC 7517）。 */
@Getter
@Setter
public class InternalJwksResponseBody {

    /**
     * 当前可用于验签的公开密钥集合
     * <li>首个元素为当前签名密钥；其后为仍在验签窗口内的轮换中密钥。</li>
     */
    private List<InternalPublicKey> keys;
}
