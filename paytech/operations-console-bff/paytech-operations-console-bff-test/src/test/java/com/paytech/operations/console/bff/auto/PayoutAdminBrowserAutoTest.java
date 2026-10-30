package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_LABELS_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PayoutOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.payout.api.PayoutHeaders;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryBlockReason;
import com.paytech.payout.api.enums.BeneficiaryCloseReason;
import com.paytech.payout.api.enums.BeneficiaryEventAction;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.BeneficiaryRejectReason;
import com.paytech.payout.api.enums.BeneficiaryRevealReason;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;
import com.paytech.payout.api.enums.BeneficiarySuspendReason;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.api.enums.PayoutErrorCode;

import tools.jackson.databind.json.JsonMapper;

/**
 * 收款账户核验与 Owner 账户 / 余额（/payout-admin/v1）：真实 BFF 入站 → PLT-I02（Platform Simulator）→ payout / client-master 测试端点
 * <li>聚合：队列与详情补 Owner 法定名，详情与列表补运营操作人显示名；主档失败只降级该 Owner。</li>
 * <li>转发：操作人取会话 Account ID、类型 OPERATIONS；读取与 Reveal 声明 X-Caller-Service；浏览器凭据与 Idempotency-Key 不外传。</li>
 * <li>payout 的合同错误码（四眼 403、重复 409）原样到达浏览器；授权拒绝与非法 Query 不调用 payout。</li>
 *
 * @see com.paytech.operations.console.bff.ext.rest.PayoutAdminController
 * @author Claude
 */
class PayoutAdminBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final String REQUEST_ID = "payout-admin-request";
    private static final String ACCOUNTS = "/payout/v1/beneficiary-accounts";
    private static final String CLIENT_ID = "80014";
    private static final String PARTNER_ID = "3002";
    private static final String ACCOUNT_ID = "900100";
    private static final String MAKER_ID = "20002";
    private static final String CONTENT_HASH = "a".repeat(64);

    @Autowired
    private PlatformOperationsSimulator platform;

    @Autowired
    private PayoutOwnerSimulator payout;

    @Autowired
    private ClientReviewOwnerSimulator clientMaster;

    @LocalServerPort
    private int port;

    /** 每例从空 Owner 配置开始，会话身份为数字运营账号。 */
    @BeforeEach
    void setUpPayoutMocks() {
        platform.reset();
        platform.useCommercialIdentity();
        payout.reset();
        clientMaster.reset();
    }

    /** payout、client-master 与 Platform 都不得收到未配置的请求；无论断言结果都清空。 */
    @AfterEach
    void verifyPayoutOwnerCalls() {
        try {
            assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
                () -> assertTrue(payout.unexpectedRequests().isEmpty(), payout.unexpectedRequests().toString()),
                () -> assertTrue(clientMaster.unexpectedRequests().isEmpty(), clientMaster.unexpectedRequests().toString()));
        } finally {
            payout.reset();
            clientMaster.reset();
        }
    }

    /** 队列：payout 分页原样带出，每行补 Owner 法定名与共用同一账号的其他 Owner 名称（同页去重）；Query 原样转发。 */
    @Test
    void test_reviewQueueAddsOwnerLegalNames() throws Exception {
        var clientAccount = account(ACCOUNT_ID, BeneficiaryOwnerType.CLIENT.getCode(), CLIENT_ID);
        clientAccount.put("sharedOwners", List.of(Map.of("ownerType", BeneficiaryOwnerType.LOCAL_PARTNER.getCode(), "ownerId",
            PARTNER_ID, "accountCode", "BA-0001", "status", BeneficiaryAccountStatus.VERIFIED.getCode())));
        var partnerAccount = account("900101", BeneficiaryOwnerType.LOCAL_PARTNER.getCode(), PARTNER_ID);
        payout.reply("GET", "/payout/v1/beneficiary-reviews", 200,
            JSON.writeValueAsString(Map.of("items", List.of(clientAccount, partnerAccount), "page", 1, "size", 20, "total", 2)));
        replyClientSummary();
        replyPartner();

        var response = send("GET", "/payout-admin/v1/beneficiary-reviews?stage=" + BeneficiaryReviewStage.MAKER.getCode()
            + "&size=20", "operations-payout-admin-review-list", null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertEquals(2, body.path("total").asInt());
        assertEquals("Atlas Trading LLC", body.path("items").get(0).path("owner").path("legalName").asString());
        assertEquals("CL-80014", body.path("items").get(0).path("owner").path("ownerNo").asString());
        assertEquals("BA-0001", body.path("items").get(0).path("account").path("sharedOwners").get(0).path("accountCode").asString());
        assertEquals("Desert Rose Partners", body.path("items").get(0).path("sharedOwnerProfiles")
            .path(BeneficiaryOwnerType.LOCAL_PARTNER.getCode() + ":" + PARTNER_ID).path("legalName").asString());
        assertEquals("Desert Rose Partners", body.path("items").get(1).path("owner").path("legalName").asString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        var call = payout.requests().getFirst();
        assertEquals(Map.of("stage", BeneficiaryReviewStage.MAKER.getCode(), "page", "1", "size", "20"), query(call.query()));
        assertEquals(2, clientMaster.requests().size());
    }

    /** 队列：某个 Owner 主档不可用时该行 owner 为空，其余照常。 */
    @Test
    void test_reviewQueueDegradesUnavailableOwner() throws Exception {
        payout.reply("GET", "/payout/v1/beneficiary-reviews", 200, JSON.writeValueAsString(Map.of("items",
            List.of(account(ACCOUNT_ID, BeneficiaryOwnerType.CLIENT.getCode(), CLIENT_ID),
                account("900101", BeneficiaryOwnerType.LOCAL_PARTNER.getCode(), PARTNER_ID)),
            "page", 1, "size", 10, "total", 2)));
        clientMaster.problem("GET", "/client-master-admin/v1/clients/" + CLIENT_ID + "/summary", 503, "SERVICE_UNAVAILABLE");
        replyPartner();

        var response = send("GET", "/payout-admin/v1/beneficiary-reviews?stage=" + BeneficiaryReviewStage.CHECKER.getCode(),
            "operations-payout-admin-review-list", null);

        assertEquals(200, response.statusCode(), response.body());
        var items = JSON.readTree(response.body()).path("items");
        assertTrue(items.get(0).path("owner").isNull(), response.body());
        assertEquals(CLIENT_ID, items.get(0).path("account").path("ownerId").asString());
        assertEquals("Desert Rose Partners", items.get(1).path("owner").path("legalName").asString());
    }

    /** 队列：Owner 参数不成对、每页条数不支持或未声明的 Query 在本地拒绝，不调用 payout。 */
    @Test
    void test_reviewQueueRejectsInvalidQuery() throws Exception {
        for (String query : List.of("stage=MAKER&ownerType=CLIENT", "stage=MAKER&size=30", "stage=MAKER&status=VERIFIED")) {
            var response = send("GET", "/payout-admin/v1/beneficiary-reviews?" + query, "operations-payout-admin-review-list", null);
            assertEquals(400, response.statusCode(), query + " -> " + response.body());
        }
        assertTrue(payout.requests().isEmpty(), payout.requests().toString());
    }

    /** 详情：运营视图（X-Caller-Service）、Owner 法定名与 Maker / 事件操作人显示名。 */
    @Test
    void test_detailAddsLegalNameAndOperatorLabels() throws Exception {
        var detail = account(ACCOUNT_ID, BeneficiaryOwnerType.CLIENT.getCode(), CLIENT_ID);
        detail.put("reviewMakerId", MAKER_ID);
        detail.put("reviewMakerDecision", MakerReviewDecision.APPROVE.getCode());
        detail.put("events", List.of(event(1, BeneficiaryEventAction.SUBMIT.getCode(), BeneficiaryActorType.CLIENT_MEMBER.getCode(), "70001"),
            event(2, BeneficiaryEventAction.MAKER_REVIEW.getCode(), BeneficiaryActorType.OPERATIONS.getCode(), MAKER_ID)));
        payout.reply("GET", ACCOUNTS + "/" + ACCOUNT_ID, 200, JSON.writeValueAsString(detail));
        replyClientSummary();
        platform.accountLabels(Map.of(MAKER_ID, "Lina Haddad"));

        var response = send("GET", "/payout-admin/v1/beneficiary-accounts/" + ACCOUNT_ID,
            "operations-payout-admin-beneficiary-detail", null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertEquals("Atlas Trading LLC", body.path("owner").path("legalName").asString());
        assertEquals("Lina Haddad", body.path("operatorLabels").path(MAKER_ID).asString());
        assertEquals(2, body.path("account").path("events").size());
        assertEquals(PayoutHeaders.OPERATIONS_BFF, payout.requests().getFirst().header(PayoutHeaders.CALLER_SERVICE));
        var labelCall = platform.requests().stream().filter(request -> request.path().equals(ACCOUNT_LABELS_PATH)).findFirst()
            .orElseThrow();
        assertEquals("accountIds=" + MAKER_ID, labelCall.query());
    }

    /** Maker 意见：会话账号为 Maker；浏览器凭据与 Idempotency-Key 不转发。 */
    @Test
    void test_makerReviewUsesSessionOperator() throws Exception {
        payout.reply("POST", ACCOUNTS + "/" + ACCOUNT_ID + "/maker-reviews", 200,
            JSON.writeValueAsString(account(ACCOUNT_ID, BeneficiaryOwnerType.CLIENT.getCode(), CLIENT_ID)));
        var body = Map.of("decision", MakerReviewDecision.REJECT.getCode(), "reasonCode",
            BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(), "note", "已核对营业执照",
            "clientNote", "请使用公司名下账户重新添加。", "contentHash", CONTENT_HASH, "expectedVersion", "1");

        var response = send("POST", "/payout-admin/v1/beneficiary-accounts/" + ACCOUNT_ID + "/maker-reviews",
            "operations-payout-admin-maker-review", body);

        assertEquals(200, response.statusCode(), response.body());
        var call = payout.requests().getFirst();
        var forwarded = new LinkedHashMap<String, Object>(body);
        forwarded.put("actorId", RISK_ACCOUNT_ID);
        assertEquals(JSON.valueToTree(forwarded), JSON.readTree(call.body()));
        assertAll(() -> assertEquals(REQUEST_ID, call.header("X-Request-Id")), () -> assertNull(call.header("Authorization")),
            () -> assertNull(call.header("Cookie")), () -> assertNull(call.header("Idempotency-Key")));
    }

    /** Checker 与 Maker 同一人：payout 的 403 FOUR_EYES_REQUIRED 原样到达浏览器。 */
    @Test
    void test_checkerFourEyesRejectionReachesBrowser() throws Exception {
        payout.problem("POST", ACCOUNTS + "/" + ACCOUNT_ID + "/checker-decisions", 403,
            PayoutErrorCode.FOUR_EYES_REQUIRED.getCode());

        var response = send("POST", "/payout-admin/v1/beneficiary-accounts/" + ACCOUNT_ID + "/checker-decisions",
            "operations-payout-admin-checker-decision", Map.of("decision", CheckerDecision.APPROVED.getCode(),
                "contentHash", CONTENT_HASH, "expectedVersion", "2"));

        assertEquals(403, response.statusCode(), response.body());
        assertEquals(PayoutErrorCode.FOUR_EYES_REQUIRED.getCode(), JSON.readTree(response.body()).path("code").asString());
        assertEquals(RISK_ACCOUNT_ID, JSON.readTree(payout.requests().getFirst().body()).path("actorId").asString());
    }

    /** 重复账号：payout 的 409 BENEFICIARY_DUPLICATE 原样到达浏览器。 */
    @Test
    void test_submitDuplicateReturnsConflict() throws Exception {
        payout.problem("POST", ACCOUNTS, 409, PayoutErrorCode.BENEFICIARY_DUPLICATE.getCode());

        var response = send("POST", "/payout-admin/v1/beneficiary-accounts", "operations-payout-admin-beneficiary-submit",
            submitBody());

        assertEquals(409, response.statusCode(), response.body());
        assertEquals(PayoutErrorCode.BENEFICIARY_DUPLICATE.getCode(), JSON.readTree(response.body()).path("code").asString());
    }

    /** 代提交：保持 payout 的 201，提交人为会话账号、类型 OPERATIONS。 */
    @Test
    void test_submitOnBehalfKeepsCreatedStatus() throws Exception {
        payout.reply("POST", ACCOUNTS, 201, JSON.writeValueAsString(account(ACCOUNT_ID, BeneficiaryOwnerType.LOCAL_PARTNER.getCode(),
            PARTNER_ID)));

        var response = send("POST", "/payout-admin/v1/beneficiary-accounts", "operations-payout-admin-beneficiary-submit",
            submitBody());

        assertEquals(201, response.statusCode(), response.body());
        var forwarded = JSON.readTree(payout.requests().getFirst().body());
        assertAll(() -> assertEquals(BeneficiaryActorType.OPERATIONS.getCode(), forwarded.path("actorType").asString()),
            () -> assertEquals(RISK_ACCOUNT_ID, forwarded.path("actorId").asString()),
            () -> assertEquals("AE070331234567890123456", forwarded.path("fields").path("iban").asString()));
    }

    /** Reveal：原因与查看人转发，声明 Operations BFF 调用方；响应 no-store。 */
    @Test
    void test_revealForwardsReasonWithoutCaching() throws Exception {
        payout.reply("POST", ACCOUNTS + "/" + ACCOUNT_ID + "/reveal", 200, JSON.writeValueAsString(Map.of(
            "beneficiaryAccountId", ACCOUNT_ID, "holderName", "ATLAS TRADING L.L.C", "iban", "AE070331234567890123456")));

        var response = send("POST", "/payout-admin/v1/beneficiary-accounts/" + ACCOUNT_ID + "/reveal",
            "operations-payout-admin-reveal", Map.of("reasonCode", BeneficiaryRevealReason.CHECKER_REVIEW.getCode()));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("ATLAS TRADING L.L.C", JSON.readTree(response.body()).path("holderName").asString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        var call = payout.requests().getFirst();
        assertEquals(PayoutHeaders.OPERATIONS_BFF, call.header(PayoutHeaders.CALLER_SERVICE));
        assertEquals(JSON.valueToTree(Map.of("actorId", RISK_ACCOUNT_ID, "reasonCode",
            BeneficiaryRevealReason.CHECKER_REVIEW.getCode())), JSON.readTree(call.body()));
    }

    /** 停用、恢复、关闭：不带 Owner，操作人为会话账号；原因与版本原样转发。 */
    @Test
    void test_stateCommandsForwardWithoutOwner() throws Exception {
        var routes = Map.of("suspend", BeneficiarySuspendReason.RISK_REVIEW.getCode(), "resume", "",
            "close", BeneficiaryCloseReason.OPS.getCode());
        for (var entry : routes.entrySet()) {
            payout.reply("POST", ACCOUNTS + "/" + ACCOUNT_ID + "/" + entry.getKey(), 200,
                JSON.writeValueAsString(account(ACCOUNT_ID, BeneficiaryOwnerType.CLIENT.getCode(), CLIENT_ID)));
            var body = new LinkedHashMap<String, Object>();
            if (!entry.getValue().isEmpty()) {
                body.put("reasonCode", entry.getValue());
            }
            body.put("expectedVersion", "3");

            var response = send("POST", "/payout-admin/v1/beneficiary-accounts/" + ACCOUNT_ID + "/" + entry.getKey(),
                "operations-payout-admin-" + entry.getKey(), body);

            assertEquals(200, response.statusCode(), entry.getKey() + " -> " + response.body());
            var forwarded = JSON.readTree(payout.requests().getLast().body());
            assertAll(() -> assertTrue(forwarded.path("ownerType").isMissingNode() || forwarded.path("ownerType").isNull()),
                () -> assertEquals(BeneficiaryActorType.OPERATIONS.getCode(), forwarded.path("actorType").asString()),
                () -> assertEquals(RISK_ACCOUNT_ID, forwarded.path("actorId").asString()),
                () -> assertEquals("3", forwarded.path("expectedVersion").asString()));
        }
        assertEquals(3, payout.requests().size());
    }

    /** 余额解释与字段要求：简单转发，Query 原样。 */
    @Test
    void test_balancesAndRequirementsForward() throws Exception {
        var position = new LinkedHashMap<String, Object>();
        position.put("entityNo", "AE001");
        position.put("currency", "USD");
        position.put("pending", "1240.00");
        position.put("available", "3860.50");
        position.put("locked", "0.00");
        position.put("securityDeposit", "5000.00");
        position.put("recoveryReceivable", "0.00");
        position.put("blockReasons", List.of(BeneficiaryBlockReason.ACCOUNT_COOLING.getCode()));
        payout.reply("GET", "/payout/v1/balance-explanations", 200, JSON.writeValueAsString(Map.of("ownerType",
            BeneficiaryOwnerType.CLIENT.getCode(), "ownerId", CLIENT_ID, "positions", List.of(position), "usableAccounts", List.of())));
        payout.reply("GET", "/payout/v1/beneficiary-requirements", 200, JSON.writeValueAsString(Map.of("railType",
            BeneficiaryRailType.LOCAL.getCode(), "bankCountry", "AE", "currency", "USD", "fields", List.of())));

        var balances = send("GET", "/payout-admin/v1/balances?ownerType=CLIENT&ownerId=" + CLIENT_ID,
            "operations-payout-admin-balances", null);
        var requirements = send("GET", "/payout-admin/v1/beneficiary-requirements?railType=LOCAL&bankCountry=AE&currency=USD",
            "operations-payout-admin-beneficiary-requirements", null);

        assertEquals(200, balances.statusCode(), balances.body());
        assertEquals("3860.50", JSON.readTree(balances.body()).path("positions").get(0).path("available").asString());
        assertEquals(200, requirements.statusCode(), requirements.body());
        assertEquals(Map.of("ownerType", "CLIENT", "ownerId", CLIENT_ID), query(payout.requests().get(0).query()));
        assertEquals(Map.of("railType", "LOCAL", "bankCountry", "AE", "currency", "USD"), query(payout.requests().get(1).query()));
    }

    /** PLT-I02 拒绝：403，只授权一次，payout 零调用。 */
    @Test
    void test_deniedAuthorizationSkipsPayout() throws Exception {
        platform.allow(false);

        var response = send("POST", "/payout-admin/v1/beneficiary-accounts/" + ACCOUNT_ID + "/reveal",
            "operations-payout-admin-reveal", Map.of("reasonCode", BeneficiaryRevealReason.MAKER_REVIEW.getCode()));

        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertTrue(payout.requests().isEmpty());
    }

    /** client-master Client 摘要。 */
    private void replyClientSummary() throws Exception {
        clientMaster.reply("GET", "/client-master-admin/v1/clients/" + CLIENT_ID + "/summary", 200, JSON.writeValueAsString(Map.of(
            "clientId", CLIENT_ID, "clientNo", "CL-80014", "partyId", "880001", "clientType", "ORGANIZATION", "status", "ACTIVE",
            "legalName", "Atlas Trading LLC")));
    }

    /** client-master Local Partner 详情。 */
    private void replyPartner() throws Exception {
        clientMaster.reply("GET", "/client-master-admin/v1/local-partners/" + PARTNER_ID, 200, JSON.writeValueAsString(Map.of(
            "localPartnerId", PARTNER_ID, "partnerNo", "LP-3002", "partyId", "880002", "legalName", "Desert Rose Partners",
            "status", "ACTIVE", "roles", List.of(), "changeVersion", 1, "providedEntities", List.of())));
    }

    /** payout 账户视图（PENDING_REVIEW，无 Maker / Checker）。 */
    private static Map<String, Object> account(String accountId, String ownerType, String ownerId) {
        var account = new LinkedHashMap<String, Object>();
        account.put("beneficiaryAccountId", accountId);
        account.put("ownerType", ownerType);
        account.put("ownerId", ownerId);
        account.put("accountCode", "BA-0002");
        account.put("railType", BeneficiaryRailType.LOCAL.getCode());
        account.put("bankCountry", "AE");
        account.put("currency", "USD");
        account.put("bankName", "Emirates NBD");
        account.put("holderNameMasked", "A**** T****** LLC");
        account.put("accountLast4", "4321");
        account.put("contentHash", CONTENT_HASH);
        account.put("status", BeneficiaryAccountStatus.PENDING_REVIEW.getCode());
        account.put("submittedByType", BeneficiaryActorType.CLIENT_MEMBER.getCode());
        account.put("submittedBy", "70001");
        account.put("usable", false);
        account.put("defaultAccount", false);
        account.put("version", "1");
        account.put("createTime", "2026-10-30T06:05:00Z");
        account.put("updateTime", "2026-10-30T06:05:00Z");
        return account;
    }

    /** 一条账户事件。 */
    private static Map<String, Object> event(int eventNo, String action, String actorType, String actorId) {
        var event = new LinkedHashMap<String, Object>();
        event.put("eventNo", eventNo);
        event.put("toStatus", BeneficiaryAccountStatus.PENDING_REVIEW.getCode());
        event.put("action", action);
        event.put("actorType", actorType);
        event.put("actorId", actorId);
        event.put("occurredTime", "2026-10-30T06:05:00Z");
        return event;
    }

    /** 代提交请求：AE 本地 USD 账户。 */
    private static Map<String, Object> submitBody() {
        return Map.of("ownerType", BeneficiaryOwnerType.LOCAL_PARTNER.getCode(), "ownerId", PARTNER_ID, "railType",
            BeneficiaryRailType.LOCAL.getCode(), "bankCountry", "AE", "currency", "USD", "fields", Map.of("holderName",
                "Desert Rose Partners", "iban", "AE070331234567890123456", "bankName", "Emirates NBD"));
    }

    /** 经真实 BFF 入口发送，带 Gateway 可信身份、浏览器凭据与 Idempotency-Key（证明不外传）。 */
    private HttpResponse<String> send(String method, String path, String routeCode, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", routeCode).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "payout-command-0001")
            .header("Authorization", "Bearer browser-access-token").header("Cookie", "browser-session=canary");
        if (!"GET".equals(method)) {
            builder.header("Origin", "https://operations-console.local.test").header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 解析 Query 为键值。 */
    private static Map<String, String> query(String raw) {
        var result = new LinkedHashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return result;
    }
}
