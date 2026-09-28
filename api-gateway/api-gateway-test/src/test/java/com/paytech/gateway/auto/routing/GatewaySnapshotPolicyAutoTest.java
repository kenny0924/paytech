package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.routing.snapshot.InvalidGatewaySnapshotException;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.platform.support.api.gatewayconfiguration.response.*;

/** 从完整快照编译入口验证数据库配置不可降低渠道安全或突破资源上限。 */
class GatewaySnapshotPolicyAutoTest {
    private final GatewaySnapshotCompiler compiler = new GatewaySnapshotCompiler();

    /** 保留六个渠道各自批准的认证与授权组合。 */
    @ParameterizedTest
    @CsvSource({"OPERATIONS,PUBLIC,PUBLIC", "OPERATIONS,OPERATIONS_JWT,HUMAN_AUTHENTICATED",
        "OPERATIONS,OPERATIONS_JWT,HUMAN_PERMISSION", "CLIENT,PUBLIC,PUBLIC", "CLIENT,CLIENT_JWT,HUMAN_AUTHENTICATED",
        "CLIENT,CLIENT_JWT,HUMAN_PERMISSION", "PARTNER,PUBLIC,PUBLIC", "PARTNER,PARTNER_JWT,HUMAN_AUTHENTICATED",
        "PARTNER,PARTNER_JWT,HUMAN_PERMISSION", "CLIENT_API,CLIENT_API_CREDENTIAL,API_SCOPE",
        "CHECKOUT,CHECKOUT_SESSION,CHECKOUT_RESOURCE", "CHANNEL_CALLBACK,CHANNEL_CALLBACK,CHANNEL_CALLBACK"})
    void test_legalChannelProfileAndAuthorizationContracts(String channel, String profile, String mode) {
        var draft = new Draft(channel, profile, mode);
        var runtime = compiler.compile(draft.snapshot()).catalog().all().getFirst();
        assertEquals(profile, runtime.securityProfileCode());
        assertEquals(mode, runtime.authorizationMode());
        assertEquals(draft.permission, runtime.permissionCode());
        assertEquals(draft.scope, runtime.requiredScopeCode());
    }

    /** 跨渠道身份或降低认证强度必须使整份快照拒绝。 */
    @ParameterizedTest
    @CsvSource({"OPERATIONS,CLIENT_JWT,HUMAN_AUTHENTICATED", "CLIENT,OPERATIONS_JWT,HUMAN_AUTHENTICATED",
        "PARTNER,CLIENT_JWT,HUMAN_AUTHENTICATED", "CLIENT_API,PUBLIC,API_SCOPE", "CHECKOUT,PUBLIC,CHECKOUT_RESOURCE",
        "CHANNEL_CALLBACK,PUBLIC,CHANNEL_CALLBACK", "OPERATIONS,PUBLIC,API_SCOPE", "CLIENT_API,CLIENT_API_CREDENTIAL,PUBLIC",
        "CHECKOUT,CHECKOUT_SESSION,PUBLIC", "CHANNEL_CALLBACK,CHANNEL_CALLBACK,PUBLIC",
        "OPERATIONS,PUBLIC,HUMAN_AUTHENTICATED", "OPERATIONS,OPERATIONS_JWT,PUBLIC"})
    void test_crossChannelOrWeakenedSecurityIsRejected(String channel, String profile, String mode) {
        rejects(new Draft(channel, profile, mode));
    }

    /** 权限与 Scope 绑定必须符合授权模式及稳定代码语法。 */
    @Test
    void test_permissionAndScopeBindingsAreRequiredOnlyForTheirMode() {
        for (String permission : Arrays.asList(null, "", " ", "lower", "P".repeat(129))) {
            var draft = new Draft("OPERATIONS", "OPERATIONS_JWT", "HUMAN_PERMISSION");
            draft.permission = permission;
            rejects(draft);
        }
        for (String scope : Arrays.asList(null, "", " ", "Upper", "orders..read", "s".repeat(129))) {
            var draft = new Draft("CLIENT_API", "CLIENT_API_CREDENTIAL", "API_SCOPE");
            draft.scope = scope;
            rejects(draft);
        }
        rejectChanged(d -> d.permission = "UNEXPECTED_PERMISSION");
        rejectChanged(d -> d.scope = "unexpected.scope");
    }

    /** 控制面不能把可信身份或非法名称加入请求 Header 名单。 */
    @ParameterizedTest
    @ValueSource(strings = {"connection", "x-forwarded-extra", "x-gateway-extra", "x-entry-channel",
        "x-platform-api-credential", "bad:header", "", "bad header"})
    void test_snapshotCannotForwardTrustedOrMalformedHeaders(String name) {
        rejectChanged(d -> d.cors = List.of(name));
        rejectChanged(d -> d.headers = List.of(name));
    }

    /** 重复 Header、凭据转发和 Client API CORS 配置不能扩大暴露范围。 */
    @Test
    void test_headerCollectionsAndClientApiCorsAreConstrained() {
        rejectChanged(d -> d.headers = List.of("authorization"));
        rejectChanged(d -> d.headers = List.of("X-Data", "x-data"));
        rejectChanged(d -> d.headers = Collections.nCopies(65, "x-data"));
        rejectChanged(d -> d.cors = List.of("X-Data", "x-data"));
        var api = new Draft("CLIENT_API", "CLIENT_API_CREDENTIAL", "API_SCOPE");
        api.cors = List.of("content-type");
        rejects(api);
        var valid = new Draft();
        valid.cors = List.of("authorization");
        assertDoesNotThrow(() -> compiler.compile(valid.snapshot()));
    }

    /** 查询参数保持唯一，机器调用不得借用人工渠道 Cookie。 */
    @Test
    void test_queryAndCookiePoliciesCannotEscapeTheirChannel() {
        rejectChanged(d -> d.queries = List.of("page", "page"));
        rejectChanged(d -> d.queries = List.of("page[]"));
        for (String cookieSide : List.of("request", "response")) {
            var api = new Draft("CLIENT_API", "CLIENT_API_CREDENTIAL", "API_SCOPE");
            if (cookieSide.equals("request")) api.requestCookie = "REQUIRE_PROFILE_COOKIE";
            else api.responseCookie = "ISSUE_ONLY";
            rejects(api);
        }
    }

    /** 数据库配置不能取消请求体或超时的绝对安全上限。 */
    @Test
    void test_bodyAndTimeoutLimitsRejectZeroNegativeOrExcessiveConfiguration() {
        rejectChanged(d -> d.body = -1);
        rejectChanged(d -> d.body = 65_537);
        rejectChanged(d -> d.receive = 0);
        rejectChanged(d -> d.receive = 5_001);
        rejectChanged(d -> d.response = 0);
        rejectChanged(d -> d.response = 10_001);
        var minimum = new Draft();
        minimum.body = 0;
        minimum.receive = 1;
        minimum.response = 1;
        assertDoesNotThrow(() -> compiler.compile(minimum.snapshot()));
    }

    /** 不支持的快照版本、重复端点和跨渠道目标都必须整体拒绝。 */
    @Test
    void test_invalidEnvelopeAndTargetIdentityRejectWholeSnapshot() {
        var ordinary = new Draft().snapshot();
        for (var invalid : Arrays.asList(null, new GatewayPublicSnapshotResponseBody(2, List.of(), List.of()),
            new GatewayPublicSnapshotResponseBody(1, Collections.nCopies(129, ordinary.getTargets().getFirst()), List.of()),
            new GatewayPublicSnapshotResponseBody(1, List.of(ordinary.getTargets().getFirst(), ordinary.getTargets().getFirst()), List.of()))) {
            assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(invalid));
        }
        for (String code : Arrays.asList(null, "", "AB", "x".repeat(129))) {
            assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(new GatewayPublicSnapshotResponseBody(1,
                List.of(new GatewayTargetSnapshot(code, "OPERATIONS", "owner-service")), List.of())));
            rejectChanged(d -> d.code = code);
        }
        for (String discovery : Arrays.asList(null, "s".repeat(256))) {
            assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(new GatewayPublicSnapshotResponseBody(1,
                List.of(new GatewayTargetSnapshot("owner", "OPERATIONS", discovery)), List.of())));
        }
        var wrongChannel = new GatewayTargetSnapshot("owner", "CLIENT", "owner-service");
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(new GatewayPublicSnapshotResponseBody(1,
            List.of(wrongChannel), ordinary.getRoutes())));
        var other = new Draft();
        other.code = "other-route";
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(new GatewayPublicSnapshotResponseBody(1,
            ordinary.getTargets(), List.of(ordinary.getRoutes().getFirst(), other.route()))));
    }

    /** Guard 参数缺失、互斥字段混用或越界时不能编译。 */
    @ParameterizedTest
    @MethodSource("invalidGuardRules")
    void test_guardNumericAndNullableFieldsRejectInvalidPolicies(GatewayGuardRuleSnapshot rule) {
        rejectChanged(d -> d.guards = List.of(rule));
    }

    /** 分别构造 Rate 和 Concurrency 的空值、互斥字段及边界错误输入。 */
    private static Stream<GatewayGuardRuleSnapshot> invalidGuardRules() {
        var values = new ArrayList<GatewayGuardRuleSnapshot>();
        for (Long number : Arrays.asList(null, 0L, -1L, 601L)) {
            values.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", number, 1L, 1L, null));
            values.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 1L, number, 1L, null));
        }
        for (Long period : Arrays.asList(null, 0L, -1L, 60_001L))
            values.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 1L, 1L, period, null));
        values.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 1L, 1L, 1L, 1L));
        for (Long limit : Arrays.asList(null, 0L, -1L, 51L))
            values.add(new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, limit));
        values.add(new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", 1L, null, null, 1L));
        values.add(new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, 1L, null, 1L));
        values.add(new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, 1L, 1L));
        return values.stream();
    }

    /** 共享基线、维度配对和认证主体约束不能因动态配置而丢失。 */
    @Test
    void test_guardsMustBeUniquePairedAndAppropriateForAuthentication() {
        rejectChanged(d -> d.guards = Collections.nCopies(9, d.guards.getFirst()));
        rejectChanged(d -> d.guards = List.of(d.guards.getFirst(), d.guards.getFirst()));
        rejectChanged(d -> d.guards = GatewaySnapshotFixture.clientApiSnapshot().getRoutes().getFirst().getGuardRules());
        for (String removed : List.of("CLIENT_IP", "AUTHENTICATED_PRINCIPAL")) {
            var draft = new Draft("OPERATIONS", "OPERATIONS_JWT", "HUMAN_AUTHENTICATED");
            draft.guards = draft.guards.stream().filter(g -> !g.getDimension().equals(removed)).toList();
            rejects(draft);
        }
    }

    /** 以完整合法输入为起点，只改变本场景要验证的配置。 */
    private void rejectChanged(Consumer<Draft> change) {
        var draft = new Draft();
        change.accept(draft);
        rejects(draft);
    }

    /** 从公开编译入口断言整份无效快照不获准发布。 */
    private void rejects(Draft draft) {
        assertThrows(InvalidGatewaySnapshotException.class, () -> compiler.compile(draft.snapshot()));
    }

    /** 夹具只装配 Wire 输入；预期值由每个业务场景独立指定。 */
    private static final class Draft {
        String code = "configured-route";
        String channel, profile, mode, permission, scope;
        String requestCookie = "DROP", responseCookie = "NONE";
        List<String> cors = List.of(), headers = List.of(), queries = List.of();
        long body = 65_536;
        int receive = 5_000, response = 10_000;
        List<GatewayGuardRuleSnapshot> guards;
        Draft() { this("OPERATIONS", "PUBLIC", "PUBLIC"); }
        Draft(String channel, String profile, String mode) {
            this.channel = channel;
            this.profile = profile;
            this.mode = mode;
            permission = mode.equals("HUMAN_PERMISSION") ? "RULE_READ" : null;
            scope = mode.equals("API_SCOPE") ? "orders.read" : null;
            guards = profile.equals("PUBLIC") ? List.of(
                new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 600L, 600L, 60_000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 50L))
                : GatewaySnapshotFixture.clientApiSnapshot().getRoutes().getFirst().getGuardRules();
        }
        GatewayRouteSnapshot route() {
            return new GatewayRouteSnapshot(code, channel, "POST", "/api/configured", "/configured", "owner",
                profile, mode, permission, scope, requestCookie, responseCookie, cors, headers, queries,
                body, receive, response, guards);
        }
        GatewayPublicSnapshotResponseBody snapshot() {
            return new GatewayPublicSnapshotResponseBody(1,
                List.of(new GatewayTargetSnapshot("owner", channel, "owner-service")), List.of(route()));
        }
    }
}
