package com.paytech.payment.test.base;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.paytech.payment.execution.core.enums.CaptureMode;
import com.paytech.payment.test.dal.ChannelTestMapper;

/**
 * 卡支付 pay 入口测试的公共调用：新建 Intent、申请合成卡工具并提交 pay，均经真实 HTTP 入口。
 *
 * <p>
 * 卡号为公开测试卡号，消费者引用、IP 与 User-Agent 为合成值；不放 {@code @Test}。
 */
public abstract class AbstractPayCardTest extends MockTestBase {
    protected static final String CLIENT_ID = "101";
    protected static final String ENTITY_NO = "AE001";
    protected static final String CURRENCY = "AED";
    protected static final String AMOUNT = "10.00";
    protected static final String INDUSTRY_CODE = "DIGITAL_GOODS";
    protected static final String MCC_CODE = "5734";
    protected static final String COUNTRY_CODE = "AE";
    protected static final String PAYMENT_METHOD_CODE = "CARD";
    protected static final String PAN = "4111111111111111";
    protected static final String CVV = "737";
    protected static final String EXPIRY_MONTH = "12";
    protected static final String EXPIRY_YEAR = "2030";
    protected static final String CARDHOLDER_NAME = "TEST CARDHOLDER";
    protected static final String RETURN_URL = "https://merchant.local.invalid/return";
    protected static final String CONSUMER_REFERENCE = "payer-ref-001";
    protected static final String CONSUMER_IP = "203.0.113.10";
    protected static final String CONSUMER_USER_AGENT = "Mozilla/5.0 (auto test)";

    @LocalServerPort
    private int port;

    @Autowired
    protected ChannelTestMapper mapper;

    /** 指向本服务真实 HTTP 入口的客户端。 */
    protected RestClient client() {
        return RestClient.create(baseUrl());
    }

    /** 本服务真实 HTTP 入口的根地址。 */
    protected String baseUrl() {
        return "http://127.0.0.1:" + port + "/payment";
    }

    /** 新建 Intent 并申请合成卡工具；来源订单 ID 与来源提交 ID 相同。 */
    protected Payment newPayment() {
        Payment intent = newIntent();
        return new Payment(CLIENT_ID, intent.intentId, intent.submissionId,
            applyCard(String.valueOf(intent.intentId), intent.submissionId));
    }

    /** 只新建 Intent，不申请卡工具；返回的支付提交尚无支付工具 Token。 */
    protected Payment newIntent() {
        return newIntent(CLIENT_ID);
    }

    /** 为指定 Client 新建 Intent，不申请卡工具。 */
    protected Payment newIntent(String clientId) {
        long submissionId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
        var intent = new LinkedHashMap<String, Object>();
        intent.put("clientId", clientId);
        intent.put("sourceType", "COMMERCE_ORDER");
        intent.put("sourceId", String.valueOf(submissionId));
        intent.put("entityNo", ENTITY_NO);
        intent.put("clientPricingVersionId", "1");
        intent.put("industryCode", INDUSTRY_CODE);
        intent.put("mccCode", MCC_CODE);
        intent.put("consumerCountryCode", COUNTRY_CODE);
        intent.put("amount", AMOUNT);
        intent.put("currency", CURRENCY);
        intent.put("expiresAt", Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString());
        String intentId = String.valueOf(post("/payment-execution/v1/intents", intent, Map.of()).getBody().get("intentId"));
        return new Payment(clientId, Long.parseLong(intentId), submissionId, null);
    }

    /** 为同一 Intent 的新来源提交申请合成卡工具，返回支付工具 Token。 */
    protected String applyCard(String intentId, long submissionId) {
        var card = Map.of("pan", PAN, "expiryMonth", EXPIRY_MONTH, "expiryYear", EXPIRY_YEAR, "cardholderName",
            CARDHOLDER_NAME, "cvv", CVV);
        return (String)post("/payment-instrument/v1/card-tokens", Map.of("clientId", CLIENT_ID, "intentId", intentId,
            "sourceSubmissionId", String.valueOf(submissionId), "paymentMethodCode", PAYMENT_METHOD_CODE, "card", card),
            Map.of()).getBody().get("paymentInstrumentToken");
    }

    /** 在已建 Intent 的提交上附上自行申请的工具 Token，供子类提交 pay。 */
    protected Payment payment(Payment intent, String token) {
        return new Payment(intent.clientId, intent.intentId, intent.submissionId, token);
    }

    /** 发送 JSON POST，返回响应（4xx/5xx 使测试失败）。 */
    protected ResponseEntity<Map<String, Object>> post(String path, Object body, Map<String, String> headers) {
        return client().post().uri(path).contentType(MediaType.APPLICATION_JSON)
            .headers(target -> headers.forEach(target::add)).body(body).retrieve()
            .toEntity(new ParameterizedTypeReference<>() {});
    }

    /** 响应中的支付尝试 ID。 */
    protected static long attemptId(ResponseEntity<Map<String, Object>> response) {
        return Long.parseLong((String)response.getBody().get("attemptId"));
    }

    /** 一次支付提交的身份：Intent、来源提交与支付工具 Token。 */
    protected final class Payment {
        public final String clientId;
        public final long intentId;
        public final long submissionId;
        public final String token;

        Payment(String clientId, long intentId, long submissionId, String token) {
            this.clientId = clientId;
            this.intentId = intentId;
            this.submissionId = submissionId;
            this.token = token;
        }

        /** 以自动捕获提交 pay；同一对象重复调用即原提交重放。 */
        public ResponseEntity<Map<String, Object>> pay() {
            return pay(CaptureMode.AUTOMATIC);
        }

        /** 以指定捕获模式提交 pay。 */
        public ResponseEntity<Map<String, Object>> pay(CaptureMode captureMode) {
            var body = new LinkedHashMap<String, Object>();
            body.put("clientId", clientId);
            body.put("sourceSubmissionId", String.valueOf(submissionId));
            body.put("captureMode", captureMode.getCode());
            body.put("payMethod", Map.of("paymentMethodCode", PAYMENT_METHOD_CODE, "paymentInstrumentToken", token));
            body.put("returnUrl", RETURN_URL);
            body.put("consumerReference", CONSUMER_REFERENCE);
            body.put("consumerIpAddress", CONSUMER_IP);
            body.put("consumerUserAgent", CONSUMER_USER_AGENT);
            return post("/payment-execution/v1/intents/" + intentId + "/pay", body,
                Map.of("Idempotency-Key", "submission:" + submissionId));
        }

        /** 同一 Intent 用新的来源提交与新工具再次支付（前一 Attempt 已结束）。 */
        public Payment retry() {
            long next = submissionId + 1;
            return new Payment(clientId, intentId, next, applyCard(String.valueOf(intentId), next));
        }
    }
}
