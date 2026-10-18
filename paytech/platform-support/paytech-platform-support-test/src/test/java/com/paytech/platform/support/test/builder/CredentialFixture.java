package com.paytech.platform.support.test.builder;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import lombok.Builder;
import lombok.Getter;

/** 合成凭证及租户前置事实；允许非法字段，避免 Builder 提前拦截负例。 */
@Getter
@Builder
public class CredentialFixture {

    public static final Instant NOW = Instant.parse("2026-09-17T12:00:00.123456Z");
    public static final String SECRET = "Qw7rT2yU8iO4pA6sD9fG1hJ3kL5zX0cV";

    private long tenantId;
    private long ownerRefId;
    private long credentialId;
    private String keyPrefix;
    @Builder.Default
    private String secret = SECRET;
    @Builder.Default
    private String tenantType = "CLIENT";
    @Builder.Default
    private String ownerStatus = "ACTIVE";
    @Builder.Default
    private String securityStatus = "ACTIVE";
    @Builder.Default
    private int tenantVersion = 1;
    @Builder.Default
    private String credentialStatus = "ACTIVE";
    @Builder.Default
    private int credentialVersion = 1;
    @Builder.Default
    private Instant expiresTime = NOW.plusSeconds(3600);
    @Builder.Default
    private boolean tenantPresent = true;
    private String verifierPhc;

    /** 默认身份只负责隔离，不参与业务规则或预期结果计算。 */
    public static CredentialFixtureBuilder template() {
        long id = ThreadLocalRandom.current().nextLong(7_000_000_000_000_000_000L, 7_900_000_000_000_000_000L);
        return builder().tenantId(id).ownerRefId(id + 1).credentialId(id + 2)
            .keyPrefix("Plt-" + Long.toUnsignedString(id, 36));
    }

    /** 仅在请求发送和测试侧 PHC 计算时组装合成秘密，不输出到诊断文本。 */
    public String credential() {
        return keyPrefix + "." + secret;
    }
}
