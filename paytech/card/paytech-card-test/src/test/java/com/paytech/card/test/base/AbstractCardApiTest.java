package com.paytech.card.test.base;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.card.api.CardApiConstants;

/**
 * card 私网入口测试的公共调用：经真实 HTTP 入口创建工具、签发票据、凭票据读卡与管理已存卡，均以 X-Caller-Service 声明调用方
 * （internal-auth.mode=OFF）。卡号为公开测试卡号，持卡人与 CVV 为合成值；不放 {@code @Test}。
 */
public abstract class AbstractCardApiTest extends CardMockTestBase {
    protected static final String CLIENT_ID = "101";
    protected static final String PAN = "4111111111111111";
    protected static final String CVV = "737";
    protected static final String EXPIRY_MONTH = "12";
    protected static final String EXPIRY_YEAR = "2030";
    protected static final String CARDHOLDER_NAME = "TEST CARDHOLDER";

    @LocalServerPort
    private int port;

    /** 不自动重发、不跟随重定向的客户端（默认 Apache HttpClient 会重发 503）。 */
    protected RestClient client() {
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).baseUrl("http://127.0.0.1:" + port)
            .build();
    }

    /** 新的来源提交 ID。 */
    protected static long submission() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }

    /** 手输卡输入：月份与 CVV 取默认值。 */
    protected static Map<String, String> card(String pan, String expiryYear, String cardholderName) {
        return Map.of("pan", pan, "expiryMonth", EXPIRY_MONTH, "expiryYear", expiryYear, "cardholderName", cardholderName,
            "cvv", CVV);
    }

    /** 创建工具请求体：期限上限为 30 分钟后。 */
    protected static Map<String, Object> instrumentBody(long submissionId, Map<String, ?> card, String customerId,
        boolean consentToSave) {
        var body = new HashMap<String, Object>();
        body.put("clientId", CLIENT_ID);
        body.put("sourceSubmissionId", Long.toString(submissionId));
        body.put("card", card);
        body.put("consentToSave", consentToSave);
        body.put("expiresAt", Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString());
        if (customerId != null) {
            body.put("customerId", customerId);
        }
        return body;
    }

    /** 已存卡加本次 CVV 的创建工具请求体。 */
    protected static Map<String, Object> storedBody(long submissionId, String storedCardToken, String customerId,
        String cvv) {
        var body = instrumentBody(submissionId, null, customerId, false);
        body.remove("card");
        body.put("storedCard", Map.of("storedCardToken", storedCardToken, "cvv", cvv));
        return body;
    }

    /** 以 Payment 身份创建工具（非 2xx 使测试失败），返回响应体。 */
    protected Map<String, Object> createInstrument(Map<String, Object> body) {
        return exchange(HttpMethod.POST, "/card/v1/instruments", CardApiConstants.PAYMENT_SERVICE, body).getBody();
    }

    /** 以 Payment 身份为工具签发票据，返回票据原值。 */
    protected String issueTicket(String instrumentRef) {
        return (String)exchange(HttpMethod.POST, "/card/v1/material-tickets", CardApiConstants.PAYMENT_SERVICE,
            ticketBody(instrumentRef, 90)).getBody().get("ticket");
    }

    /** 签发票据请求体：合成的支付尝试与动作 ID。 */
    protected static Map<String, Object> ticketBody(String instrumentRef, int ttlSeconds) {
        var body = new LinkedHashMap<String, Object>();
        body.put("instrumentRef", instrumentRef);
        body.put("paymentAttemptId", "880001");
        body.put("paymentActionId", "880002");
        body.put("purpose", "CHANNEL_EXECUTION");
        body.put("ttlSeconds", ttlSeconds);
        return body;
    }

    /** 以给定调用方发送 JSON 请求，返回响应（4xx/5xx 使测试失败）。 */
    protected ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, String caller, Object body) {
        var request = client().method(method).uri(path).header(CardApiConstants.CALLER_HEADER, caller);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(new ParameterizedTypeReference<>() {});
    }

    /** 以给定调用方发送请求，返回 HTTP 状态与响应文本（含 4xx/5xx）。 */
    protected Response call(HttpMethod method, String path, String caller, Object body) {
        try {
            var entity = exchangeText(method, path, caller, body);
            return new Response(entity.getStatusCode().value(), entity.getBody() == null ? "" : entity.getBody());
        } catch (RestClientResponseException failure) {
            return new Response(failure.getStatusCode().value(), failure.getResponseBodyAsString());
        }
    }

    private ResponseEntity<String> exchangeText(HttpMethod method, String path, String caller, Object body) {
        var request = client().method(method).uri(path);
        if (caller != null) {
            request.header(CardApiConstants.CALLER_HEADER, caller);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.retrieve().toEntity(String.class);
    }

    /** 查询结果中的数字列。 */
    protected static long id(Map<String, Object> row, String column) {
        return ((Number)row.get(column)).longValue();
    }

    /**
     * 一次 HTTP 响应
     *
     * @param status HTTP 状态
     * @param body 响应文本
     */
    protected record Response(int status, String body) {
    }
}
