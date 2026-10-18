package com.paytech.platform.support.api.internalauth.response;

import lombok.Getter;
import lombok.Setter;

/** 一把 P-256 公钥 JWK；只含公开成员。 */
@Getter
@Setter
public class InternalPublicKey {

    /**
     * 密钥类型
     * <li>固定 {@code EC}。</li>
     */
    private String kty;

    /**
     * 密钥标识
     * <li>与 Token Header {@code kid} 一致。</li>
     */
    private String kid;

    /**
     * 用途
     * <li>固定 {@code sig}。</li>
     */
    private String use;

    /**
     * 签名算法
     * <li>固定 {@code ES256}。</li>
     */
    private String alg;

    /**
     * 曲线
     * <li>固定 {@code P-256}。</li>
     */
    private String crv;

    /**
     * 公钥 X 坐标
     * <li>无填充 Base64URL。</li>
     */
    private String x;

    /**
     * 公钥 Y 坐标
     * <li>无填充 Base64URL。</li>
     */
    private String y;
}
