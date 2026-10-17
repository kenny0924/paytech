package com.paytech.platform.support.api.authentication.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 可公开发布的 RSA JSON Web Key；不包含私钥或签名引用。 */
public final class JsonWebKeyResponseBody {
    /** 密钥类型代码。 */
    private final String kty;

    /** Realm 内稳定的公开密钥标识。 */
    private final String kid;

    /** 密钥用途代码。 */
    private final String use;

    /** 允许的密钥操作代码，对外字段为 key_ops。 */
    private final List<String> keyOperations;

    /** 签名算法代码。 */
    private final String alg;

    /** 无填充 Base64URLUInt RSA 模数。 */
    private final String n;

    /** 无填充 Base64URLUInt RSA 指数。 */
    private final String e;

    /** 保存完整公开响应，集合使用不可变副本。 */
    @JsonCreator
    public JsonWebKeyResponseBody(@JsonProperty("kty") String kty,
        @JsonProperty("kid") String kid,
        @JsonProperty("use") String use,
        @JsonProperty("key_ops") List<String> keyOperations,
        @JsonProperty("alg") String alg,
        @JsonProperty("n") String n,
        @JsonProperty("e") String e) {
        this.kty = kty;
        this.kid = kid;
        this.use = use;
        this.keyOperations = List.copyOf(keyOperations);
        this.alg = alg;
        this.n = n;
        this.e = e;
    }

    /** 密钥类型代码。 */
    public String getKty() {
        return kty;
    }

    /** Realm 内稳定的公开密钥标识。 */
    public String getKid() {
        return kid;
    }

    /** 密钥用途代码。 */
    public String getUse() {
        return use;
    }

    /** 允许的密钥操作代码，对外字段为 key_ops。 */
    @JsonProperty("key_ops")
    public List<String> getKeyOperations() {
        return keyOperations;
    }

    /** 签名算法代码。 */
    public String getAlg() {
        return alg;
    }

    /** 无填充 Base64URLUInt RSA 模数。 */
    public String getN() {
        return n;
    }

    /** 无填充 Base64URLUInt RSA 指数。 */
    public String getE() {
        return e;
    }
}
