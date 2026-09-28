package com.paytech.platform.support.auto.ext.http.apicredential.validate;

import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.test.base.ApiCredentialValidationTestBase;
import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.CredentialScopeFixture;

/**
 * 可信 Client 身份、Scope 归属、去重及精确 UTC 有效期。
 *
 * @see ApiCredentialValidationApi#validate(String)
 */
class ApiCredentialValidationSuccessTest extends ApiCredentialValidationTestBase {

    private CredentialFixture fixture;

    /** 本类每例均需要独立 ACTIVE CLIENT 凭证，初始没有 Scope。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = credentialMocker.create(CredentialFixture.template().build());
    }

    /** 没有 Scope 仍可认证，路由授权由 Gateway 另行决定。 */
    @Test
    void test_emptyScopesRemainValidIdentity() {
        invoke(fixture.credential()).assertIdentity(fixture);
    }

    /** 同 Code 不同授权行去重，Wire 明确保持字典序。 */
    @Test
    void test_scopesAreSortedAndDistinct() {
        for (String scope : new String[] {"payments.read", "orders.write", "orders.read", "orders.read"}) {
            credentialMocker.scope(fixture, CredentialScopeFixture.template(scope).build());
        }
        invoke(fixture.credential()).assertIdentity(fixture, "orders.read", "orders.write", "payments.read");
    }

    /** 验证半开区间，包含开始时点，不包含结束时点。 */
    @ParameterizedTest(name = "Scope 有效期场景 {index}")
    @MethodSource("scopeWindows")
    void test_scopeWindow(String status, Instant from, Instant to, boolean effective) {
        credentialMocker.scope(fixture,
            CredentialScopeFixture.template("orders.read").status(status).effectiveFrom(from).effectiveTo(to).build());
        invoke(fixture.credential()).assertIdentity(fixture, effective ? new String[] {"orders.read"} : new String[0]);
    }

    /** 用独立业务预期覆盖微秒边界和历史授权状态。 */
    static Stream<Arguments> scopeWindows() {
        Instant now = CredentialFixture.NOW;
        return Stream.of(Arguments.of("ACTIVE", now, null, true),
            Arguments.of("ACTIVE", now.minusSeconds(1), now.plusNanos(1000), true),
            Arguments.of("ACTIVE", now.plusNanos(1000), null, false),
            Arguments.of("ACTIVE", now.minusSeconds(1), now, false),
            Arguments.of("ACTIVE", now.minusSeconds(2), now.minusNanos(1000), false),
            Arguments.of("REVOKED", now.minusSeconds(1), null, false),
            Arguments.of("EXPIRED", now.minusSeconds(1), null, false));
    }

    /** 64 个有效唯一 Scope 合法，重复行和失效行不应消耗额度。 */
    @Test
    void test_maximumDistinctScopesWithDuplicateAndInactiveRows() {
        String[] scopes = IntStream.range(0, 64).mapToObj(i -> "scope.s%02d".formatted(i)).toArray(String[]::new);
        for (String scope : scopes) {
            credentialMocker.scope(fixture, CredentialScopeFixture.template(scope).build());
        }
        credentialMocker.scope(fixture, CredentialScopeFixture.template(scopes[0]).build());
        credentialMocker.scope(fixture, CredentialScopeFixture.template("extra.revoked").status("REVOKED").build());
        invoke(fixture.credential()).assertIdentity(fixture, scopes);
    }

    /** Scope 长度上限按协议接受，不自行缩小授权代码范围。 */
    @Test
    void test_maximumScopeCodeLength() {
        String scope = "s".repeat(128);
        credentialMocker.scope(fixture, CredentialScopeFixture.template(scope).build());
        invoke(fixture.credential()).assertIdentity(fixture, scope);
    }

    /** Tenant 与 Scope 只能来自持久化归属，伪造调用方 Header 无权覆盖。 */
    @Test
    void test_otherCredentialScopesAndForgedIdentityAreIgnored() {
        var other = credentialMocker.create(CredentialFixture.template().build());
        credentialMocker.scope(other, CredentialScopeFixture.template("payments.admin").build());
        credentialMocker.scope(fixture, CredentialScopeFixture.template("orders.read").build());
        var headers = new HttpHeaders();
        headers.add(ApiCredentialConstraints.CREDENTIAL_HEADER, fixture.credential());
        headers.add("X-Gateway-Client-Id", Long.toString(other.getOwnerRefId()));
        headers.add("X-Gateway-Credential-Id", Long.toString(other.getCredentialId()));
        headers.add("X-Gateway-Tenant-Id", Long.toString(other.getTenantId()));
        invoke(headers).assertIdentity(fixture, "orders.read");
    }

    /** 到期前最后一微秒仍有效。 */
    @Test
    void test_credentialValidUntilExpiryBoundary() {
        var boundary = credentialMocker
            .create(CredentialFixture.template().expiresTime(CredentialFixture.NOW.plusNanos(1000)).build());
        invoke(boundary.credential()).assertIdentity(boundary);
    }

    /** 分别覆盖最短和最长合法裸 Credential。 */
    @ParameterizedTest
    @MethodSource("credentialLengths")
    void test_credentialLengthBoundaries(int prefixLength, int secretLength) {
        String prefix = fixture.getKeyPrefix();
        prefix = prefixLength == 8 ? prefix.substring(prefix.length() - 8)
            : prefix + "x".repeat(prefixLength - prefix.length());
        var boundary = credentialMocker
            .create(CredentialFixture.template().keyPrefix(prefix).secret("A".repeat(secretLength)).build());
        invoke(boundary.credential()).assertIdentity(boundary);
    }

    /** 最长合法凭证的总长仍为 256。 */
    static Stream<Arguments> credentialLengths() {
        return Stream.of(Arguments.of(8, 32), Arguments.of(32, 223));
    }

    /** 不缓存先前认证成功，撤销后下一次真实请求立即拒绝。 */
    @Test
    void test_committedRevocationIsVisibleOnNextRequest() {
        invoke(fixture.credential()).assertIdentity(fixture);
        credentialMocker.revoke(fixture);
        invoke(fixture.credential()).assertUnauthenticated();
    }
}
