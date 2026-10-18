package com.paytech.platform.support.auto.ext.http.apicredential.validate;

import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.test.base.ApiCredentialValidationTestBase;
import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.CredentialScopeFixture;

/**
 * 损坏的关联、PHC、版本和有效授权失败关闭，503 不泄漏原因或部分可信身份。
 *
 * @see ApiCredentialValidationApi#validate(String)
 */
class ApiCredentialValidationUnavailableTest extends ApiCredentialValidationTestBase {

    private CredentialFixture fixture;

    /** 为 Scope 负例准备当前类独立的合法认证事实。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = credentialMocker.create(CredentialFixture.template().build());
    }

    /** 数据库没有 FK，不存在的 Tenant 必须由认证链识别为关联损坏。 */
    @Test
    void test_missingTenant() {
        var damaged = credentialMocker.create(CredentialFixture.template().tenantPresent(false).build());
        invoke(damaged.credential()).assertUnavailable();
    }

    /** 持久化值损坏不能当作正常生命周期失效。 */
    @ParameterizedTest(name = "损坏身份场景 {0}")
    @MethodSource("damagedIdentities")
    void test_damagedIdentity(String label, CredentialFixture.CredentialFixtureBuilder builder) {
        var damaged = credentialMocker.create(builder.build());
        invoke(damaged.credential()).assertUnavailable();
    }

    /** 保留真实表约束，只准备数据库可保存但业务禁止的事实。 */
    static Stream<Arguments> damagedIdentities() {
        return Stream.of(Arguments.of("credential-version", CredentialFixture.template().credentialVersion(0)),
            Arguments.of("tenant-version", CredentialFixture.template().tenantVersion(0)),
            Arguments.of("owner-reference", CredentialFixture.template().ownerRefId(0)),
            Arguments.of("credential-status", CredentialFixture.template().credentialStatus("CORRUPT")),
            Arguments.of("tenant-type", CredentialFixture.template().tenantType("CORRUPT")),
            Arguments.of("owner-status", CredentialFixture.template().ownerStatus("CORRUPT")),
            Arguments.of("security-status", CredentialFixture.template().securityStatus("CORRUPT")));
    }

    /** 畸形 PHC 和超出批准计算成本的参数必须快速失败关闭。 */
    @ParameterizedTest(name = "PHC 结构场景 {index}")
    @MethodSource("invalidVerifiers")
    void test_invalidVerifier(String phc) {
        var damaged = credentialMocker.create(CredentialFixture.template().verifierPhc(phc).build());
        invoke(damaged.credential()).assertUnavailable();
    }

    /** 只使用合成 Base64；越界参数不应进入昂贵的 Argon2 计算。 */
    static Stream<String> invalidVerifiers() {
        String salt = Base64.getEncoder().withoutPadding().encodeToString(new byte[16]);
        String hash = Base64.getEncoder().withoutPadding().encodeToString(new byte[32]);
        String tail = "$" + salt + "$" + hash;
        return Stream.of("", " ", "$invalid-phc", "$argon2i$v=19$m=19456,t=2,p=1" + tail,
            "$argon2id$v=16$m=19456,t=2,p=1" + tail, "$argon2id$v=19$m=19455,t=2,p=1" + tail,
            "$argon2id$v=19$m=262145,t=2,p=1" + tail, "$argon2id$v=19$m=19456,t=1,p=1" + tail,
            "$argon2id$v=19$m=19456,t=11,p=1" + tail, "$argon2id$v=19$m=19456,t=2,p=2" + tail,
            "$argon2id$v=19$m=19456,t=2,p=1$YWJj$" + hash, "$argon2id$v=19$m=19456,t=2,p=1$" + salt + "$YWJj");
    }

    /** ACTIVE 授权的非法代码不能静默忽略或规范化为其他权限。 */
    @ParameterizedTest
    @ValueSource(strings = {"", "Orders.Read", "orders.*", "orders..read", "orders/read"})
    void test_invalidEffectiveScope(String scope) {
        credentialMocker.scope(fixture, CredentialScopeFixture.template(scope).build());
        invoke(fixture.credential()).assertUnavailable();
    }

    /** 有效授权版本必须为正。 */
    @Test
    void test_invalidScopeVersion() {
        credentialMocker.scope(fixture, CredentialScopeFixture.template("orders.read").version(0).build());
        invoke(fixture.credential()).assertUnavailable();
    }

    /** GROUP BY 去重不能掩盖同 Code 的损坏有效行。 */
    @Test
    void test_duplicateScopeWithCorruptVersion() {
        credentialMocker.scope(fixture, CredentialScopeFixture.template("orders.read").build());
        credentialMocker.scope(fixture, CredentialScopeFixture.template("orders.read").version(0).build());
        invoke(fixture.credential()).assertUnavailable();
    }

    /** 超过 64 个唯一有效 Scope 必须失败，不能截断后返回成功。 */
    @Test
    void test_tooManyEffectiveScopes() {
        for (int i = 0; i < 65; i++) {
            credentialMocker.scope(fixture, CredentialScopeFixture.template("scope.s" + i).build());
        }
        invoke(fixture.credential()).assertUnavailable();
    }
}
