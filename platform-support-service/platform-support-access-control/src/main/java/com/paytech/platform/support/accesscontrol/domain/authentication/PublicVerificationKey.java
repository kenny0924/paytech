package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 已校验且可公开发布的 RSA 验签材料，不含签名密钥引用或持久化状态。
 *
 * @param kty 密钥类型代码
 * @param kid Realm 内稳定密钥标识
 * @param use 密钥用途代码
 * @param keyOperations 允许的密钥操作代码
 * @param alg 签名算法代码
 * @param n 无填充 Base64URLUInt RSA 模数
 * @param e 无填充 Base64URLUInt RSA 指数
 */
public record PublicVerificationKey(String kty, String kid, String use,
    @JsonProperty("key_ops") List<String> keyOperations, String alg, String n, String e) {

    public PublicVerificationKey {
        keyOperations = List.copyOf(keyOperations);
    }
}
