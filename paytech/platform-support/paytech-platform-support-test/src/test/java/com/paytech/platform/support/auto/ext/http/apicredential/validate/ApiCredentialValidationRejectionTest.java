package com.paytech.platform.support.auto.ext.http.apicredential.validate;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.test.base.ApiCredentialValidationTestBase;
import com.paytech.platform.support.test.builder.CredentialFixture;

/**
 * 格式错误、错误秘密和正常失效统一返回 401，数据库事实保持不变。
 *
 * @see ApiCredentialValidationApi#validate(String)
 */
class ApiCredentialValidationRejectionTest extends ApiCredentialValidationTestBase {

    private CredentialFixture fixture;

    /** 有效凭证作为对照，不依赖执行順序或上一例数据。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = credentialMocker.create(CredentialFixture.template().build());
    }

    /** 缺失、长度、分隔符和字符集错误都保留认证 401 特殊合同。 */
    @ParameterizedTest(name = "凭证格式场景 {index}")
    @MethodSource("invalidCredentials")
    void test_invalidCredential(String credential) {
        invoke(credential).assertUnauthenticated();
    }

    /** 合成负例不会经过 Builder 或调用器的修复。 */
    static Stream<Arguments> invalidCredentials() {
        String secret = CredentialFixture.SECRET;
        return Stream.of(Arguments.of((Object)null), Arguments.of(""), Arguments.of("short"),
            Arguments.of("prefix7." + secret), Arguments.of("p".repeat(33) + "." + secret),
            Arguments.of("prefix00." + "a".repeat(31)), Arguments.of("prefix00." + "a".repeat(224)),
            Arguments.of("prefix00" + secret), Arguments.of("prefix00.." + secret), Arguments.of("prefix_0." + secret),
            Arguments.of("-prefix0." + secret), Arguments.of("prefix00." + secret + "="),
            Arguments.of("prefix00." + secret + "/"), Arguments.of("Bearer prefix00." + secret),
            Arguments.of("prefix00." + secret + ",other"));
    }

    /** 唯一索引的数据库排序规则不得让大小写不同的 Prefix 通过。 */
    @Test
    void test_caseSensitivePrefix() {
        invoke(fixture.getKeyPrefix().toLowerCase(java.util.Locale.ROOT) + "." + CredentialFixture.SECRET)
            .assertUnauthenticated();
    }

    /** 格式正确但不存在的凭证仍使用相同错误合同。 */
    @Test
    void test_unknownPrefix() {
        invoke(CredentialFixture.template().build().credential()).assertUnauthenticated();
    }

    /** Prefix 相同不能代替完整 PHC 秘密校验。 */
    @Test
    void test_wrongSecret() {
        invoke(fixture.getKeyPrefix() + "." + "B".repeat(32)).assertUnauthenticated();
    }

    /** 不能从多值 Header 挑选一条有效凭证。 */
    @Test
    void test_duplicateCredentialHeaders() {
        var headers = new HttpHeaders();
        headers.add(ApiCredentialConstraints.CREDENTIAL_HEADER, fixture.credential());
        headers.add(ApiCredentialConstraints.CREDENTIAL_HEADER, fixture.credential());
        invoke(headers).assertUnauthenticated();
    }

    /** 凭证终态即使到期时间仍在未来也拒绝。 */
    @ParameterizedTest
    @ValueSource(strings = {"REVOKED", "EXPIRED"})
    void test_inactiveCredential(String status) {
        var rejected = credentialMocker.create(CredentialFixture.template().credentialStatus(status).build());
        invoke(rejected.credential()).assertUnauthenticated();
    }

    /** 到期当时和之前均拒绝，不受系统执行快慢影响。 */
    @ParameterizedTest
    @MethodSource("expiredTimes")
    void test_expiredCredential(Instant expiry) {
        var rejected = credentialMocker.create(CredentialFixture.template().expiresTime(expiry).build());
        invoke(rejected.credential()).assertUnauthenticated();
    }

    /** 明确给出截止边界，不复算生产判断。 */
    static Stream<Instant> expiredTimes() {
        return Stream.of(CredentialFixture.NOW, CredentialFixture.NOW.minusNanos(1000));
    }

    /** 非 Client 或任一租户状态失效都不能返回身份。 */
    @ParameterizedTest
    @MethodSource("inactiveTenants")
    void test_inactiveOrNonClientTenant(String type, String owner, String security) {
        var rejected = credentialMocker
            .create(CredentialFixture.template().tenantType(type).ownerStatus(owner).securityStatus(security).build());
        invoke(rejected.credential()).assertUnauthenticated();
    }

    /** 覆盖全部当前合法非活动状态，不把未知数据库值混作普通失效。 */
    static Stream<Arguments> inactiveTenants() {
        return Stream.of(Arguments.of("PARTNER", "ACTIVE", "ACTIVE"), Arguments.of("INTERNAL", "ACTIVE", "ACTIVE"),
            Arguments.of("CLIENT", "PENDING", "ACTIVE"), Arguments.of("CLIENT", "SUSPENDED", "ACTIVE"),
            Arguments.of("CLIENT", "TERMINATED", "ACTIVE"), Arguments.of("CLIENT", "ACTIVE", "SUSPENDED"));
    }
}
