package com.paytech.finance.auto.balance;

import static com.paytech.finance.test.FinanceProblemChecker.assertProblem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.finance.FinanceApplication;

import tools.jackson.databind.json.JsonMapper;

/** 余额公开入口到真实 MyBatis/MySQL 的完整只读链路。 */
@ActiveProfiles("auto")
@SpringBootTest(classes = FinanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BalanceQueryHttpAutoTest {

    private static final String REQUEST_ID = "Finance.Balance_01";
    private static final long BOOK_ID = 8_810_001L;
    private static final long CHART_ID = 8_820_001L;
    private static final long CLIENT_ID = 8_830_001L;
    private static final long PARTNER_ID = 8_840_001L;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        clearFixtures();
        jdbc.update("INSERT INTO t_ledger_book(id,entity_type,entity_id,currency,chart_id,book_status,version,create_time,update_time) VALUES(?, 'OPERATING_ENTITY', ?, 'USD', ?, 'OPEN', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
            BOOK_ID, 8_850_001L, CHART_ID);
        insertChart(8_821_001L, "CLIENT_PAYABLE", "LIABILITY", "CREDIT");
        insertChart(8_821_002L, "CLIENT_SECURITY_DEPOSIT", "LIABILITY", "CREDIT");
        insertChart(8_821_003L, "CLIENT_RECOVERY_RECEIVABLE", "ASSET", "DEBIT");
        insertChart(8_821_004L, "PARTNER_PAYABLE", "LIABILITY", "CREDIT");
        insertChart(8_821_005L, "PARTNER_RECOVERY_RECEIVABLE", "ASSET", "DEBIT");
    }

    @AfterEach
    void tearDown() {
        clearFixtures();
    }

    @Test
    void test_clientQueryReturnsFiveAccountBucketsWithSignedExactMoney() throws Exception {
        insertAccount(8_860_001L, 8_821_001L, "CLIENT", CLIENT_ID, "PENDING", "CREDIT", "ACTIVE", "100.25000000");
        insertAccount(8_860_002L, 8_821_001L, "CLIENT", CLIENT_ID, "AVAILABLE", "CREDIT", "INACTIVE", "70.00000000");
        insertAccount(8_860_003L, 8_821_001L, "CLIENT", CLIENT_ID, "LOCKED", "CREDIT", "CLOSED", "30.00000000");
        insertAccount(8_860_004L, 8_821_002L, "CLIENT", CLIENT_ID, "NONE", "CREDIT", "ACTIVE", "500.00000000");
        insertAccount(8_860_005L, 8_821_003L, "CLIENT", CLIENT_ID, "NONE", "DEBIT", "ACTIVE", "-1.50000000");

        HttpResponse<String> response = get("/balances/v1/clients/" + CLIENT_ID + query());
        Assertions.assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        Assertions.assertEquals("100.25", body.get("pendingAmount").get("amount").asString());
        Assertions.assertEquals("70", body.get("availableAmount").get("amount").asString());
        Assertions.assertEquals("30", body.get("lockedAmount").get("amount").asString());
        Assertions.assertEquals("500", body.get("securityDepositAmount").get("amount").asString());
        Assertions.assertEquals("-1.5", body.get("recoveryReceivableAmount").get("amount").asString());
    }

    @Test
    void test_partnerQueryReturnsMissingBucketsAsCurrencyZero() throws Exception {
        insertAccount(8_860_011L, 8_821_004L, "LOCAL_PARTNER", PARTNER_ID, "AVAILABLE", "CREDIT", "ACTIVE", "20");

        HttpResponse<String> response = get("/balances/v1/partners/" + PARTNER_ID + query());
        Assertions.assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        Assertions.assertEquals("0", body.get("pendingAmount").get("amount").asString());
        Assertions.assertEquals("20", body.get("availableAmount").get("amount").asString());
        Assertions.assertEquals("0", body.get("lockedAmount").get("amount").asString());
        Assertions.assertEquals("0", body.get("recoveryReceivableAmount").get("amount").asString());
        Assertions.assertFalse(body.has("securityDepositAmount"));
    }

    @Test
    void test_invalidOrUnknownScopeFailsClosed() throws Exception {
        assertProblem(json, get("/balances/v1/clients/0" + query()), 400, "INVALID_REQUEST", "请求参数不正确",
            REQUEST_ID);
        assertProblem(json, get("/balances/v1/clients/9223372036854775808" + query()), 400, "INVALID_REQUEST",
            "业务标识必须为正整数且不超过 BIGINT 范围", REQUEST_ID);
        assertProblem(json, get("/balances/v1/clients/" + CLIENT_ID
            + "?operatingEntityId=8850002&currency=USD"), 400, "BUSINESS_CHECK_FAILED",
            "未找到指定经营主体和币种的账簿", REQUEST_ID);
        assertProblem(json, get("/balances/v1/clients/" + CLIENT_ID
            + "?operatingEntityId=8850001&currency=ZZZ"), 400, "INVALID_REQUEST", "币种不是可识别的货币代码",
            REQUEST_ID);
    }

    @Test
    void test_inconsistentAccountProjectionFailsClosed() throws Exception {
        insertAccount(8_860_021L, 8_821_001L, "CLIENT", CLIENT_ID, "AVAILABLE", "DEBIT", "ACTIVE", "20");

        HttpResponse<String> response = get("/balances/v1/clients/" + CLIENT_ID + query());
        assertProblem(json, response, 500, "INTERNAL_ERROR", "系统处理失败", REQUEST_ID);
    }

    private String query() {
        return "?operatingEntityId=8850001&currency=USD";
    }

    private void insertChart(long id, String code, String accountClass, String normalSide) {
        jdbc.update("INSERT INTO t_chart_account(id,chart_id,parent_account_id,account_code,account_name,account_class,normal_side,posting_allowed,create_time,update_time) VALUES(?,?,NULL,?,?,?, ?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            id, CHART_ID, code, code, accountClass, normalSide);
    }

    private void insertAccount(long id, long chartAccountId, String ownerType, long ownerId, String bucket,
        String normalSide, String status, String balance) {
        jdbc.update("INSERT INTO t_account(id,ledger_book_id,chart_account_id,owner_type,owner_id,balance_bucket,normal_side,account_status,debit_total,credit_total,net_balance,last_journal_sequence,balance_version,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,0,0,?,0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            id, BOOK_ID, chartAccountId, ownerType, ownerId, bucket, normalSide, status, balance);
    }

    private void clearFixtures() {
        jdbc.update("DELETE FROM t_account WHERE id BETWEEN 8860000 AND 8869999");
        jdbc.update("DELETE FROM t_chart_account WHERE id BETWEEN 8820000 AND 8829999");
        jdbc.update("DELETE FROM t_ledger_book WHERE id BETWEEN 8810000 AND 8819999");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("X-Request-Id", REQUEST_ID).GET().build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
