package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.JwtSigningAlgorithm;
import com.paytech.platform.support.accesscontrol.domain.enums.JwtSigningKeyStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** JWT 签名密钥查询投影，仅在 Access Control 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class JwtSigningKey {
    /** JWT 签名密钥的 Snowflake 主键。 */
    private Long signingKeyId;

    /** 隔离 issuer、audience 与 KMS Key Ring 的认证域。 */
    private AuthenticationRealm realm;

    /** Realm 内稳定且公开的 JWT Header 密钥标识。 */
    private String kid;

    /** JWT 签名算法；当前只允许 RS256。 */
    private JwtSigningAlgorithm algorithm;

    /** 公钥 JWK Canonical JSON 的结构版本。 */
    private Integer publicJwkSchemaVersion;

    /** 只包含公开 RSA 验签材料的 Canonical JWK JSON。 */
    private String publicJwkCanonicalJson;

    /** JWT 签名密钥的当前生命周期状态。 */
    private JwtSigningKeyStatus status;

    /** 每个 Realm 唯一 ACTIVE 密钥的占位值。 */
    private Integer activeSlot;

    /** 密钥允许参与签发或发布的最早时刻。 */
    private Instant notBefore;

    /** RETIRING 密钥停止签发 Access JWT 的时刻。 */
    private Instant signingEndTime;

    /** RETIRING 密钥停止出现在 JWKS 的时刻。 */
    private Instant verificationEndTime;
}
