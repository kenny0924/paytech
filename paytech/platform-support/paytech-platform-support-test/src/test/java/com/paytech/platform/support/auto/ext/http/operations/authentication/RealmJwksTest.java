package com.paytech.platform.support.auto.ext.http.operations.authentication;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.authentication.CustomerJwksQueryApi;
import com.paytech.platform.support.api.authentication.OperationsJwksQueryApi;
import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.TestAuthenticationKeys;

import tools.jackson.databind.node.ObjectNode;

/**
 * 两个 Realm 的 JWKS：ACTIVE 与轮换窗口内 RETIRING 公钥的发布，以及任一非法候选导致整体失败关闭。
 *
 * @see OperationsJwksQueryApi#queryOperationsJwks()
 * @see CustomerJwksQueryApi#queryCustomerJwks()
 */
class RealmJwksTest extends OperationsApiTestBase {

    @Autowired
    private TestAuthenticationKeys keys;

    /** 只发布 ACTIVE 与仍在验签窗口的 RETIRING；已过验签截止的密钥不发布，ACTIVE 排在最前。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"OPERATIONS", "CUSTOMER"})
    void test_publishesActiveAndRetiringKeys(String realm) {
        Instant now = operationsMocker.now();
        operationsData.deleteSigningKeys(realm);
        insert(realm, "a-retiring", 1, json -> json, "RETIRING", null, now.minusSeconds(7200), now.minusSeconds(60),
            now.plusSeconds(3600));
        insert(realm, "b-active", 1, json -> json, "ACTIVE", 1, now.minusSeconds(60), null, null);
        insert(realm, "c-expired", 1, json -> json, "RETIRING", null, now.minusSeconds(9000), now.minusSeconds(7200),
            now.minusSeconds(1));

        var jwks = jwks(realm).assertOk().as(JsonWebKeySetResponseBody.class);

        assertEquals(List.of("b-active", "a-retiring"), jwks.getKeys().stream().map(key -> key.getKid()).toList());
        var active = jwks.getKeys().getFirst();
        assertAll(() -> assertEquals("RSA", active.getKty()), () -> assertEquals("sig", active.getUse()),
            () -> assertEquals("RS256", active.getAlg()),
            () -> assertEquals(List.of("verify"), active.getKeyOperations()));
        var raw = jwks(realm).assertOk().json().path("keys");
        assertEquals(2, raw.size());
        raw.valueStream().forEach(key -> assertEquals(java.util.Set.of("kty", "kid", "use", "key_ops", "alg", "n", "e"),
            java.util.Set.copyOf(key.propertyNames()), "只发布公开 RSA 字段"));
    }

    /** 候选中任一非法时整体拒绝发布，返回不可用的服务端错误。 */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidCandidates")
    void test_invalidCandidateFailsClosed(String realm, String scenario, Candidate candidate) {
        Instant now = operationsMocker.now();
        operationsData.deleteSigningKeys(realm);
        if (!candidate.replacesActive()) {
            insert(realm, "valid-active", 1, json -> json, "ACTIVE", 1, now.minusSeconds(60), null, null);
        }
        insert(realm, candidate.kid(), candidate.schemaVersion(), candidate.jwk(), candidate.status(),
            candidate.activeSlot(), now.plusSeconds(candidate.notBefore()),
            candidate.signingEnd() == null ? null : now.plusSeconds(candidate.signingEnd()),
            candidate.verificationEnd() == null ? null : now.plusSeconds(candidate.verificationEnd()));

        jwks(realm).assertProblem(500, "INTERNAL_ERROR");
    }

    /** 没有任何 ACTIVE 密钥时不发布空集合。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"OPERATIONS", "CUSTOMER"})
    void test_missingActiveKeyFailsClosed(String realm) {
        operationsData.deleteSigningKeys(realm);

        jwks(realm).assertProblem(500, "INTERNAL_ERROR");
    }

    /** 超过 16 把可发布密钥时拒绝。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"OPERATIONS", "CUSTOMER"})
    void test_tooManyKeysFailsClosed(String realm) {
        Instant now = operationsMocker.now();
        for (int index = 0; index < 16; index++) {
            insert(realm, "retiring-" + index, 1, json -> json, "RETIRING", null, now.minusSeconds(7200),
                now.minusSeconds(60), now.plusSeconds(3600));
        }

        jwks(realm).assertProblem(500, "INTERNAL_ERROR");
    }

    static Stream<Arguments> invalidCandidates() {
        List<Arguments> cases = new java.util.ArrayList<>();
        for (String realm : List.of("OPERATIONS", "CUSTOMER")) {
            cases.add(Arguments.of(realm, "ACTIVE 尚未生效", Candidate.active().notBefore(60)));
            cases.add(Arguments.of(realm, "ACTIVE 带截止时间", Candidate.active().ends(-60L, 3600L)));
            cases.add(Arguments.of(realm, "ACTIVE 未占用 Slot", Candidate.active().slot(null)));
            cases.add(Arguments.of(realm, "RETIRING 占用 Slot", Candidate.retiring().slot(1).replacing()));
            cases.add(Arguments.of(realm, "RETIRING 缺截止时间", Candidate.retiring().ends(null, null)));
            cases.add(Arguments.of(realm, "RETIRING 仍在签名窗口", Candidate.retiring().ends(60L, 3600L)));
            cases.add(Arguments.of(realm, "RETIRING 重叠不足", Candidate.retiring().ends(-60L, 800L)));
            cases.add(Arguments.of(realm, "RETIRING 生效晚于签名截止", Candidate.retiring().notBefore(-30).ends(-60L, 3600L)));
            cases.add(Arguments.of(realm, "结构版本不支持", Candidate.retiring().schema(2)));
            cases.add(Arguments.of(realm, "JWK 不是对象", Candidate.retiring().raw("[1]")));
            cases.add(Arguments.of(realm, "JWK 含私钥字段", Candidate.retiring().edit(json -> json.put("d", "AQAB"))));
            cases.add(Arguments.of(realm, "kty 非 RSA", Candidate.retiring().edit(json -> json.put("kty", "EC"))));
            cases.add(Arguments.of(realm, "kid 与记录不一致", Candidate.retiring().edit(json -> json.put("kid", "other"))));
            cases.add(Arguments.of(realm, "kid 格式非法", Candidate.retiring().kid("bad kid!")));
            cases.add(Arguments.of(realm, "use 非 sig", Candidate.retiring().edit(json -> json.put("use", "enc"))));
            cases.add(Arguments.of(realm, "alg 非 RS256", Candidate.retiring().edit(json -> json.put("alg", "RS512"))));
            cases.add(Arguments.of(realm, "key_ops 非 verify", Candidate.retiring().edit(json -> {
                json.putArray("key_ops").add("sign");
                return json;
            })));
            cases.add(Arguments.of(realm, "key_ops 多值", Candidate.retiring().edit(json -> {
                json.putArray("key_ops").add("verify").add("sign");
                return json;
            })));
            cases.add(Arguments.of(realm, "kty 非字符串", Candidate.retiring().edit(json -> json.put("kty", 1))));
            cases.add(Arguments.of(realm, "n 非 Base64URL", Candidate.retiring().edit(json -> json.put("n", "n+/="))));
            cases.add(Arguments.of(realm, "n 带前导零", Candidate.retiring().edit(json -> json.put("n", "AAEC"))));
            cases.add(Arguments.of(realm, "n 非规范编码", Candidate.retiring().edit(json -> json.put("n", "AQF"))));
            cases.add(Arguments.of(realm, "模数不足 2048", Candidate.retiring().edit(json -> json.put("n", "AQAB"))));
            cases.add(Arguments.of(realm, "指数为偶数", Candidate.retiring().edit(json -> json.put("e", "Ag"))));
            cases.add(Arguments.of(realm, "指数为 1", Candidate.retiring().edit(json -> json.put("e", "AQ"))));
        }
        return cases.stream();
    }

    private void insert(String realm, String kid, int schemaVersion, UnaryOperator<ObjectNode> jwk, String status,
        Integer slot, Instant notBefore, Instant signingEnd, Instant verificationEnd) {
        ObjectNode json = (ObjectNode)objectMapper.readTree(keys.publicJwk(realm, kid));
        String value = objectMapper.writeValueAsString(jwk.apply(json));
        operationsData.insertSigningKey(OperatorBuilder.nextId(), realm, kid, keys.keyRef(realm, kid), schemaVersion,
            value, status, slot, notBefore, signingEnd, verificationEnd, operationsMocker.now());
    }

    private void insert(String realm, String kid, int schemaVersion, Candidate.Jwk jwk, String status, Integer slot,
        Instant notBefore, Instant signingEnd, Instant verificationEnd) {
        if (jwk.raw() != null) {
            operationsData.insertSigningKey(OperatorBuilder.nextId(), realm, kid, keys.keyRef(realm, kid),
                schemaVersion, jwk.raw(), status, slot, notBefore, signingEnd, verificationEnd, operationsMocker.now());
            return;
        }
        insert(realm, kid, schemaVersion, jwk.edit(), status, slot, notBefore, signingEnd, verificationEnd);
    }

    private PlatformResponseChecker jwks(String realm) {
        return anonymous(HttpMethod.GET, "/authentication/v1/realms/" + realm + "/jwks", null);
    }

    /** 单个候选密钥的场景参数；时间以秒为单位相对应用时钟。 */
    record Candidate(String kid, int schemaVersion, Jwk jwkSpec, String status, Integer activeSlot, long notBefore,
        Long signingEnd, Long verificationEnd, boolean replacesActive) {

        static Candidate active() {
            return new Candidate("candidate", 1, new Jwk(json -> json, null), "ACTIVE", 1, -60, null, null, true);
        }

        static Candidate retiring() {
            return new Candidate("candidate", 1, new Jwk(json -> json, null), "RETIRING", null, -7200, -60L, 3600L,
                false);
        }

        Candidate notBefore(long seconds) {
            return new Candidate(kid, schemaVersion, jwkSpec, status, activeSlot, seconds, signingEnd, verificationEnd,
                replacesActive);
        }

        Candidate ends(Long signing, Long verification) {
            return new Candidate(kid, schemaVersion, jwkSpec, status, activeSlot, notBefore, signing, verification,
                replacesActive);
        }

        Candidate slot(Integer slot) {
            return new Candidate(kid, schemaVersion, jwkSpec, status, slot, notBefore, signingEnd, verificationEnd,
                replacesActive);
        }

        Candidate replacing() {
            return new Candidate(kid, schemaVersion, jwkSpec, status, activeSlot, notBefore, signingEnd,
                verificationEnd, true);
        }

        Candidate schema(int version) {
            return new Candidate(kid, version, jwkSpec, status, activeSlot, notBefore, signingEnd, verificationEnd,
                replacesActive);
        }

        Candidate kid(String value) {
            return new Candidate(value, schemaVersion, jwkSpec, status, activeSlot, notBefore, signingEnd,
                verificationEnd, replacesActive);
        }

        Candidate raw(String json) {
            return new Candidate(kid, schemaVersion, new Jwk(null, json), status, activeSlot, notBefore, signingEnd,
                verificationEnd, replacesActive);
        }

        Candidate edit(UnaryOperator<ObjectNode> edit) {
            return new Candidate(kid, schemaVersion, new Jwk(edit, null), status, activeSlot, notBefore, signingEnd,
                verificationEnd, replacesActive);
        }

        Jwk jwk() {
            return jwkSpec;
        }

        @Override
        public String toString() {
            return kid;
        }

        /** 公钥 JWK 的修改方式或原始 JSON。 */
        record Jwk(UnaryOperator<ObjectNode> edit, String raw) {
        }
    }
}
