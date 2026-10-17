package com.paytech.foundation.internalauth.core.token;

import java.util.Objects;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;

/**
 * 用 P-256 私钥把声明签成 ES256 JWS Compact；Platform 签发端与本地静态密钥共用。
 *
 * @author Claude
 */
public final class InternalTokenSigner {

    private final ECKey signingKey;
    private final ECDSASigner signer;

    /**
     * 创建签名器。
     *
     * @param signingKey 含私钥与 {@code kid} 的 P-256 JWK
     * @throws IllegalArgumentException 不是 P-256 私钥或缺少 {@code kid}
     */
    public InternalTokenSigner(ECKey signingKey) {
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey must not be null");
        if (!Curve.P_256.equals(signingKey.getCurve()) || !signingKey.isPrivate() || signingKey.getKeyID() == null) {
            throw new IllegalArgumentException("signing key must be a P-256 private JWK with kid");
        }
        try {
            this.signer = new ECDSASigner(signingKey);
        } catch (JOSEException exception) {
            throw new IllegalArgumentException("signing key is unusable", exception);
        }
    }

    /**
     * 签名。
     *
     * @param claims 已校验的声明
     * @return JWS Compact
     */
    public String sign(InternalTokenClaims claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.getKeyID())
            .type(JOSEObjectType.JWT).build();
        SignedJWT jwt = new SignedJWT(header, claims.toJwtClaims());
        try {
            jwt.sign(signer);
        } catch (JOSEException exception) {
            throw new IllegalStateException("internal token signing failed", exception);
        }
        return jwt.serialize();
    }

    /**
     * 对应的公钥 JWK。
     *
     * @return 不含私钥的 JWK
     */
    public ECKey publicKey() {
        return signingKey.toPublicJWK();
    }
}
