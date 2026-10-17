package com.paytech.platform.support.core.internalauth;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenKeySource;
import com.paytech.foundation.internalauth.core.token.InternalTokenSigner;

/**
 * 签发端的内部调用 Token 密钥集合：一把当前签名私钥与若干轮换中公钥，纯内存，不访问外部依赖。
 * <li>JWKS 首个元素为当前签名公钥；轮换中公钥只用于验签，kid 不得重复。</li>
 * <li>同时作为 Platform 自身入站校验的公钥来源，Foundation 自动配置因此不再要求 {@code internal-auth.jwks-uri}。</li>
 */
public final class InternalTokenKeyRing implements InternalTokenKeySource {

    private final InternalTokenSigner signer;
    private final List<ECKey> publishedKeys;

    /**
     * 解析并校验配置的密钥材料；任何不合法的材料都拒绝启动，错误信息不含密钥内容。
     *
     * @param privateJwk 含 kid 的 P-256 私钥 JWK JSON
     * @param retiringPublicJwks 轮换中的 P-256 公钥 JWK JSON
     * @throws IllegalStateException 材料缺失、不是 P-256 或 kid 重复
     */
    public InternalTokenKeyRing(String privateJwk, List<String> retiringPublicJwks) {
        ECKey signingKey = parse(privateJwk, "private-jwk");
        if (!signingKey.isPrivate()) {
            throw new IllegalStateException("internal-token.issuer.private-jwk 必须是含私钥的 P-256 JWK");
        }
        try {
            this.signer = new InternalTokenSigner(signingKey);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("internal-token.issuer.private-jwk 必须是含 kid 的 P-256 私钥", invalid);
        }
        List<ECKey> keys = new ArrayList<>();
        keys.add(published(signer.publicKey()));
        for (String retiring : Objects.requireNonNullElse(retiringPublicJwks, List.<String>of())) {
            ECKey key = parse(retiring, "retiring-public-jwks");
            if (key.isPrivate()) {
                throw new IllegalStateException("internal-token.issuer.retiring-public-jwks 只能包含公钥");
            }
            keys.add(published(key));
        }
        requireUniqueKeyIds(keys);
        this.publishedKeys = List.copyOf(keys);
    }

    /**
     * 用当前签名私钥签名。
     *
     * @param claims 已校验的声明
     * @return ES256 JWS Compact
     */
    public String sign(InternalTokenClaims claims) {
        return signer.sign(claims);
    }

    /**
     * 当前发布的公钥：签名公钥在前，轮换中公钥在后。
     *
     * @return 不含私钥成员的 JWK
     */
    public List<ECKey> publishedKeys() {
        return publishedKeys;
    }

    /**
     * 按 kid 查找发布中的公钥，供本服务入站校验。
     */
    @Override
    public ECKey findKey(String keyId) {
        return publishedKeys.stream().filter(key -> key.getKeyID().equals(keyId)).findFirst().orElse(null);
    }

    /** 解析 EC JWK；错误只给出配置键。 */
    private static ECKey parse(String json, String name) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("internal-token.issuer." + name + " 未配置");
        }
        try {
            ECKey key = ECKey.parse(json);
            if (!Curve.P_256.equals(key.getCurve()) || key.getKeyID() == null || key.getKeyID().isBlank()) {
                throw new IllegalStateException("internal-token.issuer." + name + " 必须是含 kid 的 P-256 JWK");
            }
            return key;
        } catch (ParseException invalid) {
            throw new IllegalStateException("internal-token.issuer." + name + " 不是合法的 EC JWK");
        }
    }

    /** 统一发布形态：公钥、用途 sig、算法 ES256。 */
    private static ECKey published(ECKey key) {
        return new ECKey.Builder(key.toPublicJWK()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).build();
    }

    /** 同一 JWKS 中 kid 必须唯一，否则校验方无法确定公钥。 */
    private static void requireUniqueKeyIds(List<ECKey> keys) {
        Set<String> keyIds = new HashSet<>();
        for (ECKey key : keys) {
            if (!keyIds.add(key.getKeyID())) {
                throw new IllegalStateException("internal-token.issuer 发布的公钥 kid 重复：" + key.getKeyID());
            }
        }
    }
}
