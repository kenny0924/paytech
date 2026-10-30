package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.builder.TaxOrderResponseBuilder.DOCUMENT_ID;
import static com.paytech.operations.console.bff.test.builder.TaxOrderResponseBuilder.DOCUMENT_NUMBER;
import static com.paytech.operations.console.bff.test.builder.TaxOrderResponseBuilder.FILE_ID;
import static com.paytech.operations.console.bff.test.builder.TaxOrderResponseBuilder.ORDER_NO;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.TAX_VIEW_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.TaxCalloutKind;
import com.paytech.operations.console.bff.domain.enums.TaxPageStatus;
import com.paytech.operations.console.bff.domain.enums.TaxProgressState;
import com.paytech.operations.console.bff.domain.enums.TaxProgressStep;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.builder.TaxOrderResponseBuilder;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.FinanceOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.operations.console.bff.test.simulator.TaxOwnerSimulator;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxNotManagedReason;
import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.api.response.TaxOrderResponseBody;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 财务 → 税务查询（AC-13、AC-14）：真实 BFF 入站 → PLT-I02（Platform Simulator）→ tax 订单查询（Tax Simulator）→ 页面模型；
 * 下载再经 Platform 内部文件接口签发地址
 * <li>页面模型按原型 16（已登记）、17（阻断）、18（不由平台计税）及暂估 / 待支付核对状态、进度、提示卡、金额与右栏。</li>
 * <li>tax 查询不带操作人与浏览器凭据；下载以操作人签发，Platform 记审计。</li>
 */
class TaxOrderBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String REQUEST_ID = "tax-browser-request";
    private static final String DETAIL_ROUTE = "operations-finance-tax-order-detail";
    private static final String DOWNLOAD_ROUTE = "operations-finance-tax-document-download";
    private static final String TAX_PATH = "/tax/v1/orders/" + ORDER_NO;
    private static final String PAGE_PATH = "/tax/v1/orders/" + ORDER_NO;
    private static final String DOWNLOAD_PAGE_PATH = PAGE_PATH + "/documents/" + DOCUMENT_ID + "/download";
    private static final String FILE_DOWNLOAD_PATH = "/internal/files/v1/files/" + FILE_ID + "/download";
    private static final String CLIENT_PATH = "/client-master-admin/v1/clients/" + TaxOrderResponseBuilder.CLIENT_ID;
    private static final String ENTITY_PATH = "/client-master-admin/v1/operating-entities/" + TaxOrderResponseBuilder.ENTITY_NO;
    private static final String CLIENT_DETAIL = "{\"client\":{\"clientId\":\"" + TaxOrderResponseBuilder.CLIENT_ID
        + "\",\"status\":\"ACTIVE\",\"legalName\":\"Nimbus Notes\"},\"values\":{},\"people\":[],\"relations\":[],\"applications\":[]}";
    /** client-master 经营主体详情（字段集合与 EntityQualificationAdminBrowserAutoTest 一致，严格解码要求完整形状）。 */
    private static final String ENTITY_DETAIL = "{\"entityNo\":\"" + TaxOrderResponseBuilder.ENTITY_NO + "\",\"partyId\":\""
        + TaxOrderResponseBuilder.SUBJECT_PARTY_ID + "\",\"legalName\":\"Demo PayFac OE\","
        + "\"operatingCountry\":\"AE\",\"status\":\"ACTIVE\",\"acquiringQualification\":\"PAYFAC\",\"changeVersion\":3,"
        + "\"sourceRelation\":{\"sourceRelationId\":\"5001\",\"sourceType\":\"PARTNER\",\"sourceClientId\":null,\"sourcePartnerId\":\"9001\","
        + "\"effectiveFrom\":\"2026-10-24T00:00:00.000000Z\",\"effectiveTo\":null},\"provider\":{\"localPartnerId\":\"9001\","
        + "\"partnerNo\":\"LP9001\",\"partyId\":\"880000000003\",\"legalName\":\"Our PayFac\",\"status\":\"ACTIVE\","
        + "\"roles\":[\"OE_PROVIDER\"]},\"scopes\":[],\"sellerDisclosures\":[]}";
    private static final String DOWNLOAD = "{\"url\":\"https://storage.local.test/receipt?sig=1\","
        + "\"expiresAt\":\"2026-10-29T06:50:00Z\",\"disposition\":\"ATTACHMENT\"}";

    @Autowired
    private PlatformOperationsSimulator platform;

    @Autowired
    private TaxOwnerSimulator tax;

    @Autowired
    private ClientReviewOwnerSimulator owners;

    @LocalServerPort
    private int port;

    /** 每例从默认授权、数字操作人、空 tax 配置与可读的 Client / Operating Entity 名称开始。 */
    @BeforeEach
    void prepareTaxPage() {
        platform.reset();
        platform.useCommercialIdentity();
        tax.reset();
        owners.reset();
        owners.reply("GET", CLIENT_PATH, 200, CLIENT_DETAIL);
        owners.reply("GET", ENTITY_PATH, 200, ENTITY_DETAIL);
    }

    /** Platform 与 tax 不得收到未配置的请求；无论断言结果都清空配置与挂起响应。 */
    @AfterEach
    void verifyTaxPageCalls() {
        try {
            assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
                () -> assertTrue(tax.unexpectedRequests().isEmpty(), tax.unexpectedRequests().toString()),
                () -> assertTrue(owners.unexpectedRequests().isEmpty(), owners.unexpectedRequests().toString()));
        } finally {
            platform.reset();
            tax.reset();
            owners.reset();
        }
    }

    /** 原型 16：已登记、已开票、底稿缺 SAR 汇率；tax 只被调用一次且不带操作人与浏览器凭据。 */
    @Test
    void test_registeredOrderPage() throws Exception {
        replyOrder(TaxOrderResponseBuilder.registered());

        var response = send("GET", PAGE_PATH, DETAIL_ROUTE);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode page = JSON.readTree(response.body());
        assertEquals(TaxPageStatus.ISSUED.getCode(), page.path("pageStatus").asString());
        assertAll(() -> assertEquals("Nimbus Notes", page.path("buyerClientName").asString()),
            () -> assertEquals("Demo PayFac OE", page.path("entityName").asString()),
            () -> assertEquals("Demo PayFac OE", page.path("taxSubjectName").asString()));
        assertNameCallsCarryOperator();
        assertEquals("PLATFORM_OPERATIONS", page.path("managedBy").asString());
        assertSteps(page, TaxProgressState.DONE, TaxProgressState.DONE, TaxProgressState.DONE, TaxProgressState.DONE,
            TaxProgressState.ATTENTION);
        assertEquals("SA", page.path("steps").get(1).path("countryCode").asString());
        assertEquals("115.00", page.path("steps").get(2).path("amount").asString());
        assertEquals(DOCUMENT_NUMBER, page.path("steps").get(3).path("documentNumber").asString());
        assertEquals("SAR", page.path("steps").get(4).path("reportingCurrency").asString());
        var callout = page.path("callout");
        assertAll(() -> assertEquals(TaxCalloutKind.REPORTING_FX_MISSING.getCode(), callout.path("kind").asString()),
            () -> assertEquals(TaxCalloutKind.REPORTING_FX_MISSING.getTone(), callout.path("tone").asString()),
            () -> assertEquals("15.00", callout.path("taxAmount").asString()),
            () -> assertEquals("SAR", callout.path("reportingCurrency").asString()));
        assertRegisteredAmountsAndExplanation(page);
        assertRegisteredRightColumn(page);
        assertTaxCalledOnce();
    }

    /** 已开票且底稿就绪：提示为完成，底稿带期间与申报币种合计。 */
    @Test
    void test_registeredOrderWithReadyReporting() throws Exception {
        var order = TaxOrderResponseBuilder.registered();
        var transaction = order.getTransactions().getFirst();
        transaction.setReportingStatus(TaxReportingStatus.READY.getCode());
        transaction.setReportingBlockReason(null);
        transaction.setReportingEntries(List.of(TaxOrderResponseBuilder.reportingEntry()));
        replyOrder(order);

        JsonNode page = okPage();

        assertSteps(page, TaxProgressState.DONE, TaxProgressState.DONE, TaxProgressState.DONE, TaxProgressState.DONE,
            TaxProgressState.DONE);
        assertEquals(TaxCalloutKind.REGISTERED.getCode(), page.path("callout").path("kind").asString());
        var reporting = page.path("reporting");
        assertAll(() -> assertEquals("2026-10-01", reporting.path("periodStart").asString()),
            () -> assertEquals("2026-11-30", reporting.path("filingDueDate").asString()),
            () -> assertEquals("56.25", reporting.path("reportingTaxAmount").asString()));
    }

    /** 已登记、税票仍预留：标题为已登记，登记步骤待补，提示税票未开具，税票不可下载。 */
    @Test
    void test_registeredOrderWithReservedReceipt() throws Exception {
        var order = TaxOrderResponseBuilder.registered();
        var transaction = order.getTransactions().getFirst();
        transaction.setReportingStatus(TaxReportingStatus.READY.getCode());
        transaction.getDocuments().getFirst().setIssueStatus(TaxDocumentIssueStatus.RESERVED.getCode());
        transaction.getDocuments().getFirst().setDocumentFileAddress(null);
        replyOrder(order);

        JsonNode page = okPage();

        assertEquals(TaxPageStatus.REGISTERED.getCode(), page.path("pageStatus").asString());
        assertEquals(TaxProgressState.ATTENTION.getCode(), page.path("steps").get(3).path("state").asString());
        assertEquals(TaxCalloutKind.DOCUMENT_PENDING.getCode(), page.path("callout").path("kind").asString());
        assertFalse(page.path("receipt").path("downloadable").asBoolean());
    }

    /** 原型 17：最终报价被阻断；原因解析出第 2 行品类，匹配结果显示市场与规则包未命中，右栏无税票与登记。 */
    @Test
    void test_blockedOrderPage() throws Exception {
        replyOrder(TaxOrderResponseBuilder.blocked());

        JsonNode page = okPage();

        assertEquals(TaxPageStatus.BLOCKED.getCode(), page.path("pageStatus").asString());
        assertSteps(page, TaxProgressState.DONE, TaxProgressState.BLOCKED, TaxProgressState.PENDING,
            TaxProgressState.PENDING, TaxProgressState.PENDING);
        assertEquals(TaxCalloutKind.PAYMENT_BLOCKED.getCode(), page.path("callout").path("kind").asString());
        assertEquals("DANGER", page.path("callout").path("tone").asString());
        var reasons = page.path("blockReasons");
        assertAll(() -> assertEquals(2, reasons.size()),
            () -> assertEquals(TaxBlockReason.MARKET_NOT_ENABLED.getCode(), reasons.get(0).path("code").asString()),
            () -> assertEquals("KW", reasons.get(0).path("countryCode").asString()),
            () -> assertTrue(reasons.get(0).path("lineNo").isNull()),
            () -> assertEquals(TaxBlockReason.UNKNOWN_TAX_CATEGORY.getCode(), reasons.get(1).path("code").asString()),
            () -> assertEquals(2, reasons.get(1).path("lineNo").asInt()),
            () -> assertEquals("GAME_KEY", reasons.get(1).path("categoryCode").asString()));
        var matching = page.path("matching");
        assertAll(() -> assertEquals(TaxEvidenceType.BILLING_COUNTRY.getCode(), matching.path("evidenceType").asString()),
            () -> assertFalse(matching.path("marketMatched").asBoolean()),
            () -> assertFalse(matching.path("policyMatched").asBoolean()));
        assertTrue(page.path("amounts").path("taxAmount").isNull());
        assertAll(() -> assertTrue(page.path("explanation").isNull()), () -> assertTrue(page.path("receipt").isNull()),
            () -> assertTrue(page.path("registration").isNull()), () -> assertTrue(page.path("basis").isNull()));
        assertEquals(TaxCalculationStatus.BLOCKED.getCode(), page.path("quotes").get(0).path("status").asString());
    }

    /** 变更 3：税类是 Client 自由文本（空格、非 ASCII、类似标记的字符），每个未知行一条原因；BFF 原样透传行号与品类，不改写。 */
    @Test
    void test_freeTextCategoriesPassThroughPerLine() throws Exception {
        String first = "Game key <b>\u6e38\u620f</b>";
        String second = "Ebook & \"audio\" {bundle}";
        var order = TaxOrderResponseBuilder.blocked();
        var blocked = order.getCalculations().getLast();
        blocked.getLines().get(0).setCategoryCode(first);
        blocked.getLines().get(1).setCategoryCode(second);
        blocked.setBlockReasons(List.of(TaxOrderResponseBuilder.blockReason(TaxBlockReason.UNKNOWN_TAX_CATEGORY, "lines[1].categoryCode"),
            TaxOrderResponseBuilder.blockReason(TaxBlockReason.UNKNOWN_TAX_CATEGORY, "lines[2].categoryCode")));
        replyOrder(order);

        JsonNode page = okPage();

        var reasons = page.path("blockReasons");
        assertAll(() -> assertEquals(2, reasons.size()),
            () -> assertEquals(1, reasons.get(0).path("lineNo").asInt()), () -> assertEquals(first, reasons.get(0).path("categoryCode").asString()),
            () -> assertEquals(2, reasons.get(1).path("lineNo").asInt()), () -> assertEquals(second, reasons.get(1).path("categoryCode").asString()),
            () -> assertEquals(first, page.path("lines").get(0).path("categoryCode").asString()));
    }

    /** 原型 18：PSP 订单不由平台计税；支付步骤标为税务服务不记录，平台登记不适用。 */
    @Test
    void test_notManagedOrderPage() throws Exception {
        replyOrder(TaxOrderResponseBuilder.notManaged());

        JsonNode page = okPage();

        assertEquals(TaxPageStatus.NOT_MANAGED.getCode(), page.path("pageStatus").asString());
        assertEquals(List.of(TaxProgressStep.QUOTE.getCode(), TaxProgressStep.PAYMENT.getCode(),
            TaxProgressStep.REGISTRATION.getCode()), page.path("steps").valueStream().map(s -> s.path("key").asString()).toList());
        assertSteps(page, TaxProgressState.DONE, TaxProgressState.UNTRACKED, TaxProgressState.NOT_APPLICABLE);
        assertEquals("7.00", page.path("steps").get(0).path("amount").asString());
        assertEquals(TaxCalloutKind.CLIENT_RESPONSIBLE.getCode(), page.path("callout").path("kind").asString());
        var notManaged = page.path("notManaged");
        assertAll(() -> assertEquals("PSP", notManaged.path("businessMode").asString()),
            () -> assertEquals("880000000017", notManaged.path("taxSubjectPartyId").asString()),
            () -> assertEquals(TaxOrderResponseBuilder.CALCULATION_R1, notManaged.path("calculationId").asString()),
            () -> assertEquals(TaxNotManagedReason.SUBJECT_NOT_REGISTERED.getCode(), notManaged.path("reason").asString()));
        assertTrue(page.path("managedBy").isNull(), "不由平台计税没有注册，不显示操作方");
        assertEquals("DECLARED", page.path("amounts").path("taxAmountSource").asString());
        assertTrue(page.path("explanation").isNull());
        assertTrue(page.path("basis").isNull());
    }

    /** CLIENT 来源 OE（责任方未确定）：纳税主体为空，原因 TAX_RESPONSIBLE_UNRESOLVED，不标纳税主体名称，进度仍为三步。 */
    @Test
    void test_notManagedWithUnresolvedResponsibleParty() throws Exception {
        replyOrder(TaxOrderResponseBuilder.notManagedUnresolved());

        JsonNode page = okPage();

        assertAll(() -> assertEquals(TaxPageStatus.NOT_MANAGED.getCode(), page.path("pageStatus").asString()),
            () -> assertTrue(page.path("taxSubjectPartyId").isNull()), () -> assertTrue(page.path("taxSubjectName").isNull()),
            () -> assertEquals(TaxNotManagedReason.TAX_RESPONSIBLE_UNRESOLVED.getCode(), page.path("notManaged").path("reason").asString()),
            () -> assertEquals("Demo PayFac OE", page.path("entityName").asString()),
            () -> assertEquals(3, page.path("steps").size()));
    }

    /** 名称只为展示：Client 读取失败、Operating Entity 的 Party 不是纳税主体时页面仍 200，对应名称为空，页面显示 ID。 */
    @Test
    void test_partyNamesDegradeToIds() throws Exception {
        owners.problem("GET", CLIENT_PATH, 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        owners.reply("GET", ENTITY_PATH, 200, ENTITY_DETAIL.replace(TaxOrderResponseBuilder.SUBJECT_PARTY_ID, "880000000099"));
        replyOrder(TaxOrderResponseBuilder.registered());

        JsonNode page = okPage();

        assertAll(() -> assertTrue(page.path("buyerClientName").isNull()),
            () -> assertEquals("Demo PayFac OE", page.path("entityName").asString()),
            () -> assertTrue(page.path("taxSubjectName").isNull(), "收单主体不是纳税主体时不标名称"),
            () -> assertEquals(TaxOrderResponseBuilder.CLIENT_ID, page.path("buyerClientId").asString()));

        tax.reset();
        owners.reset();
        owners.problem("GET", CLIENT_PATH, 403, "OPERATIONS_PERMISSION_DENIED");
        owners.problem("GET", ENTITY_PATH, 404, "OPERATING_ENTITY_NOT_FOUND");
        replyOrder(TaxOrderResponseBuilder.registered());
        JsonNode denied = okPage();
        assertAll(() -> assertTrue(denied.path("buyerClientName").isNull()), () -> assertTrue(denied.path("entityName").isNull()));
    }

    /** 不经聚合扩大访问：只有 TAX_VIEW 的操作人看不到名称，BFF 先向 PLT-I02 核对两个详情页权限，被拒后不调用 client-master。 */
    @Test
    void test_taxViewOnlyOperatorSeesIdsOnly() throws Exception {
        platform.grantPermissions(TAX_VIEW_PERMISSION_ID);
        replyOrder(TaxOrderResponseBuilder.registered());

        JsonNode page = okPage();

        assertAll(() -> assertTrue(page.path("buyerClientName").isNull()), () -> assertTrue(page.path("entityName").isNull()),
            () -> assertTrue(page.path("taxSubjectName").isNull()),
            () -> assertEquals(TaxOrderResponseBuilder.CLIENT_ID, page.path("buyerClientId").asString()));
        assertTrue(owners.requests().isEmpty(), "无查看权限时不得读取 client-master: " + owners.requests());
        var checks = platform.requests().stream().filter(request -> AUTHORIZE_PATH.equals(request.path())).map(RecordedRequest::body).toList();
        assertTrue(checks.stream().anyMatch(body -> body.contains("\"operations-client-admin-detail\"")), checks.toString());
        assertTrue(checks.stream().anyMatch(body -> body.contains("\"operations-operating-entity-detail\"")), checks.toString());
    }

    /** 只有 Client 查看权限：只显示 Client 名称，不读取经营主体。 */
    @Test
    void test_namesFollowEachViewPermission() throws Exception {
        platform.grantPermissions(TAX_VIEW_PERMISSION_ID, PlatformOperationsSimulator.CLIENT_DETAIL_PERMISSION_ID);
        replyOrder(TaxOrderResponseBuilder.registered());

        JsonNode page = okPage();

        assertAll(() -> assertEquals("Nimbus Notes", page.path("buyerClientName").asString()),
            () -> assertTrue(page.path("entityName").isNull()), () -> assertTrue(page.path("taxSubjectName").isNull()));
        assertTrue(owners.requests().stream().noneMatch(request -> request.path().equals(ENTITY_PATH)), owners.requests().toString());
    }

    /** 只有建单暂估：暂估提示；最终报价就绪未支付：待支付提示带到期时间。 */
    @Test
    void test_estimatedAndQuotedOrders() throws Exception {
        replyOrder(TaxOrderResponseBuilder.estimated());
        JsonNode estimated = okPage();
        assertEquals(TaxPageStatus.ESTIMATED.getCode(), estimated.path("pageStatus").asString());
        assertSteps(estimated, TaxProgressState.DONE, TaxProgressState.PENDING, TaxProgressState.PENDING,
            TaxProgressState.PENDING, TaxProgressState.PENDING);
        assertEquals(TaxCalloutKind.ESTIMATE_ONLY.getCode(), estimated.path("callout").path("kind").asString());
        assertEquals(TaxCalculationStage.PROVISIONAL.getCode(), estimated.path("amounts").path("stage").asString());

        prepareTaxPage();
        replyOrder(TaxOrderResponseBuilder.quoted());
        JsonNode quoted = okPage();
        assertEquals(TaxPageStatus.QUOTED.getCode(), quoted.path("pageStatus").asString());
        assertSteps(quoted, TaxProgressState.DONE, TaxProgressState.DONE, TaxProgressState.PENDING,
            TaxProgressState.PENDING, TaxProgressState.PENDING);
        assertEquals(TaxCalloutKind.AWAITING_PAYMENT.getCode(), quoted.path("callout").path("kind").asString());
        assertEquals("2026-10-30T06:42:00Z", quoted.path("callout").path("expiresAt").asString());
    }

    /** tax 没有任何计算：404 TAX_ORDER_NOT_FOUND 到达页面，不外露 tax 原文。 */
    @Test
    void test_orderNotFound() throws Exception {
        tax.problem("GET", TAX_PATH, 404, TaxErrorCode.TAX_ORDER_NOT_FOUND.getCode());

        var response = send("GET", PAGE_PATH, DETAIL_ROUTE);

        assertProblem(response, 404, TaxErrorCode.TAX_ORDER_NOT_FOUND.getCode());
        assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_DETAIL_CANARY));
    }

    /** 订单号格式非法：本地 400，不调用 tax。 */
    @Test
    void test_invalidOrderNumber() throws Exception {
        var response = send("GET", "/tax/v1/orders/-ORD", DETAIL_ROUTE);

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(tax.requests().isEmpty(), tax.requests().toString());
    }

    /** 当前会话授权被拒：403，tax 零调用。 */
    @Test
    void test_permissionDenied() throws Exception {
        platform.allow(false);

        var response = send("GET", PAGE_PATH, DETAIL_ROUTE);

        assertEquals(403, response.statusCode(), response.body());
        assertTrue(tax.requests().isEmpty(), tax.requests().toString());
    }

    /** tax 返回其他订单或缺计算：上游响应无效 502；tax 不可用：503。 */
    @Test
    void test_upstreamInvalidOrUnavailable() throws Exception {
        var other = TaxOrderResponseBuilder.registered();
        other.setOrderNo("ORD-OTHER");
        replyOrder(other);
        assertProblem(send("GET", PAGE_PATH, DETAIL_ROUTE), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());

        tax.reset();
        var empty = TaxOrderResponseBuilder.registered();
        empty.setCalculations(List.of());
        replyOrder(empty);
        assertProblem(send("GET", PAGE_PATH, DETAIL_ROUTE), 502, CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());

        tax.reset();
        tax.problem("GET", TAX_PATH, 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertEquals(503, send("GET", PAGE_PATH, DETAIL_ROUTE).statusCode());
    }

    /** 下载：先读 tax 单据，再以操作人签发 Platform 地址；响应带单号与 60 秒地址。 */
    @Test
    void test_downloadIssuedReceipt() throws Exception {
        replyOrder(TaxOrderResponseBuilder.registered());
        platform.groupedResponses(Map.of(FILE_DOWNLOAD_PATH, DOWNLOAD));

        var response = send("POST", DOWNLOAD_PAGE_PATH, DOWNLOAD_ROUTE);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(DOCUMENT_ID, body.path("documentId").asString()),
            () -> assertEquals(DOCUMENT_NUMBER, body.path("documentNumber").asString()),
            () -> assertEquals("https://storage.local.test/receipt?sig=1", body.path("url").asString()),
            () -> assertEquals("2026-10-29T06:50:00Z", body.path("expiresAt").asString()));
        var grants = fileCalls();
        assertEquals(1, grants.size());
        var grant = grants.getFirst();
        assertAll(() -> assertEquals("POST", grant.method()),
            () -> assertEquals(RISK_ACCOUNT_ID, grant.header("X-Operator-Account-Id")),
            () -> assertNull(grant.query(), "不指定下载方式，按 Platform 默认附件下载"),
            () -> assertEquals("operations-console-bff", grant.header("X-Caller-Service")),
            () -> assertNull(grant.header("Idempotency-Key")));
        assertTaxCalledOnce();
    }

    /** 单据不属于该订单：404，不调用 Platform 文件接口。 */
    @Test
    void test_downloadUnknownDocument() throws Exception {
        replyOrder(TaxOrderResponseBuilder.registered());

        var response = send("POST", PAGE_PATH + "/documents/913000099999/download", DOWNLOAD_ROUTE);

        assertProblem(response, 404, CommonErrorCode.NOT_FOUND.getCode());
        assertTrue(fileCalls().isEmpty());
    }

    /** 税票仍预留（无文件地址）：按不存在 404 处理，不调用 Platform 文件接口。 */
    @Test
    void test_downloadReservedReceiptRejected() throws Exception {
        var order = TaxOrderResponseBuilder.registered();
        var document = order.getTransactions().getFirst().getDocuments().getFirst();
        document.setIssueStatus(TaxDocumentIssueStatus.RESERVED.getCode());
        document.setDocumentFileAddress(null);
        replyOrder(order);

        var response = send("POST", DOWNLOAD_PAGE_PATH, DOWNLOAD_ROUTE);

        assertProblem(response, 404, CommonErrorCode.NOT_FOUND.getCode());
        assertTrue(fileCalls().isEmpty());
    }

    /** 原型 21：文件服务不可用时 503 透传且不外露地址，响应带本次 Request ID 供页面复制。 */
    @Test
    void test_downloadFileServiceUnavailable() throws Exception {
        replyOrder(TaxOrderResponseBuilder.registered());
        platform.problem(FILE_DOWNLOAD_PATH, 503, "SERVICE_UNAVAILABLE");

        var response = send("POST", DOWNLOAD_PAGE_PATH, DOWNLOAD_ROUTE);

        assertEquals(503, response.statusCode(), response.body());
        assertFalse(response.body().contains("storage.local.test"));
        assertEquals(REQUEST_ID, JSON.readTree(response.body()).path("requestId").asString());
    }

    /** 下载不接受 Query：本地 400，tax 与 Platform 文件接口零调用。 */
    @Test
    void test_downloadRejectsQuery() throws Exception {
        var response = send("POST", DOWNLOAD_PAGE_PATH + "?disposition=INLINE", DOWNLOAD_ROUTE);

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(tax.requests().isEmpty());
        assertTrue(fileCalls().isEmpty());
    }

    /** 原型 16 左栏：当前报价金额、一句话计税说明、逐行明细与报价记录。 */
    private static void assertRegisteredAmountsAndExplanation(JsonNode page) {
        var amounts = page.path("amounts");
        assertAll(() -> assertEquals(2, amounts.path("revision").asInt()),
            () -> assertEquals("100.00", amounts.path("netAmount").asString()),
            () -> assertEquals("15.00", amounts.path("taxAmount").asString()),
            () -> assertEquals("115.00", amounts.path("grossAmount").asString()));
        var explanation = page.path("explanation");
        assertAll(() -> assertFalse(explanation.path("mixed").asBoolean()),
            () -> assertEquals("SA", explanation.path("jurisdictionCode").asString()),
            () -> assertEquals("0.15", explanation.path("rate").asString()),
            () -> assertEquals(TaxEvidenceType.BILLING_COUNTRY.getCode(), explanation.path("evidenceType").asString()),
            () -> assertEquals("NON_RESIDENT", explanation.path("schemeCode").asString()),
            () -> assertEquals("DEMO_SA_SAAS", explanation.path("packageCode").asString()),
            () -> assertEquals(1, explanation.path("policyRevision").asInt()));
        var lines = page.path("lines");
        assertAll(() -> assertEquals(2, lines.size()), () -> assertEquals("12.00", lines.get(0).path("taxAmount").asString()),
            () -> assertEquals("SA_REMOTE_B2C_STANDARD", lines.get(0).path("ruleCode").asString()),
            () -> assertEquals("Nimbus Notes Pro · Annual", lines.get(0).path("description").asString()),
            () -> assertTrue(lines.get(1).path("description").isNull(), "未提供说明时为空，页面显示行引用"));
        var quotes = page.path("quotes");
        assertAll(() -> assertEquals(2, quotes.get(0).path("revision").asInt()),
            () -> assertTrue(quotes.get(0).path("current").asBoolean()),
            () -> assertEquals("AE", quotes.get(0).path("previousCountryCode").asString()),
            () -> assertEquals("10.00", quotes.get(0).path("taxDelta").asString()),
            () -> assertEquals(1, quotes.get(1).path("revision").asInt()),
            () -> assertTrue(quotes.get(1).path("taxDelta").isNull()));
    }

    /** 原型 16 右栏：税票可下载、正式登记带法定时区、底稿待补、计算依据。 */
    private static void assertRegisteredRightColumn(JsonNode page) {
        var receipt = page.path("receipt");
        assertAll(() -> assertEquals(DOCUMENT_ID, receipt.path("documentId").asString()),
            () -> assertTrue(receipt.path("downloadable").asBoolean()), () -> assertFalse(receipt.has("documentFileAddress")));
        var registration = page.path("registration");
        assertAll(() -> assertEquals("Asia/Riyadh", registration.path("legalTimezone").asString()),
            () -> assertEquals(2, registration.path("calculationRevision").asInt()));
        var reporting = page.path("reporting");
        assertAll(() -> assertEquals(TaxReportingStatus.PENDING.getCode(), reporting.path("status").asString()),
            () -> assertEquals("MONTHLY", reporting.path("filingFrequency").asString()),
            () -> assertTrue(reporting.path("periodStart").isNull()));
        var basis = page.path("basis");
        assertAll(() -> assertEquals("DEMO_SA_SAAS", basis.path("packageCode").asString()),
            () -> assertTrue(basis.path("marketEnabled").asBoolean()),
            () -> assertEquals(2, basis.path("roundingScale").asInt()));
    }

    /** 进度各步状态依次相等。 */
    private static void assertSteps(JsonNode page, TaxProgressState... states) {
        assertEquals(java.util.Arrays.stream(states).map(TaxProgressState::getCode).toList(),
            page.path("steps").valueStream().map(step -> step.path("state").asString()).toList());
    }

    /** 成功读取页面模型。 */
    private JsonNode okPage() throws Exception {
        var response = send("GET", PAGE_PATH, DETAIL_ROUTE);
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    /** tax 只收到一次查询：不带操作人与浏览器凭据，传递 Request ID；PLT-I02 只授权一次。 */
    private void assertTaxCalledOnce() {
        assertEquals(1, tax.requests().size(), tax.requests().toString());
        var call = tax.requests().getFirst();
        assertAll(() -> assertEquals("GET", call.method()), () -> assertEquals(TAX_PATH, call.path()),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertNull(call.header("X-Gateway-Actor-Subject")), () -> assertNull(call.header("Authorization")),
            () -> assertNull(call.header("Cookie")), () -> assertNull(call.header("Idempotency-Key")));
        // 页面入口只授权一次；名称核对的两次 PLT-I02 调用使用详情页路由码，不计入。
        assertEquals(1, platform.requests().stream().filter(request -> AUTHORIZE_PATH.equals(request.path())
            && (request.body().contains("\"" + DETAIL_ROUTE + "\"") || request.body().contains("\"" + DOWNLOAD_ROUTE + "\""))).count());
    }

    /** client-master 名称读取带操作人可信头（管理接口按操作人授权），各一次。 */
    private void assertNameCallsCarryOperator() {
        for (String path : List.of(CLIENT_PATH, ENTITY_PATH)) {
            var calls = owners.requests().stream().filter(request -> request.path().equals(path)).toList();
            assertEquals(1, calls.size(), path);
            assertEquals(RISK_ACCOUNT_ID, calls.getFirst().header("X-Gateway-Actor-Subject"), path);
            assertEquals(SESSION_ID, calls.getFirst().header("X-Gateway-Session-Id"), path);
        }
    }

    /** 配置 tax 订单查询的成功响应。 */
    private void replyOrder(TaxOrderResponseBody order) {
        tax.reply("GET", TAX_PATH, 200, JSON.writeValueAsString(order));
    }

    /** Platform 文件下载签发的调用记录。 */
    private List<RecordedRequest> fileCalls() {
        return platform.requests().stream().filter(request -> request.path().equals(FILE_DOWNLOAD_PATH)).toList();
    }

    /** Problem 状态与错误码。 */
    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, JSON.readTree(response.body()).path("code").asString(), response.body());
    }

    /** 带 Gateway 可信头访问 BFF；写方法声明 JSON 且无 Body，并附带不得转发的浏览器凭据。 */
    private HttpResponse<String> send(String method, String path, String route) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", REQUEST_ID),
            Map.entry("Idempotency-Key", "tax-browser-key-0001"), Map.entry("Authorization", "Bearer browser-token"),
            Map.entry("Cookie", "browser-session=canary"),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", route),
            Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"),
            Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001"))
            .forEach(builder::header);
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
