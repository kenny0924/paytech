package com.paytech.platform.support.test.builder;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import lombok.Builder;
import lombok.Getter;

/** 独立 Scope 授权事实，不依赖目录或其他测试的初始化。 */
@Getter
@Builder
public class CredentialScopeFixture {

    private long scopeId;
    private String scopeCode;
    @Builder.Default
    private String status = "ACTIVE";
    @Builder.Default
    private Instant effectiveFrom = CredentialFixture.NOW.minusSeconds(3600);
    private Instant effectiveTo;
    @Builder.Default
    private int version = 1;

    /** 相同 Code 可建立不同授权行，用于验证去重和数量边界。 */
    public static CredentialScopeFixtureBuilder template(String scopeCode) {
        return builder()
            .scopeId(ThreadLocalRandom.current().nextLong(7_000_000_000_000_000_000L, 7_900_000_000_000_000_000L))
            .scopeCode(scopeCode);
    }
}
