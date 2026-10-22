package com.paytech.card.auto.internalauth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientResponseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.card.api.CardApiConstants;
import com.paytech.card.test.base.AbstractCardApiTest;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.core.token.InternalTokenSigner;

/**
 * internal-auth.mode=REQUIRED（本地静态密钥对，D26，design/05 §4）：card 入口只认内部 Token 的调用方，错调用方被拒且不返回卡数据。
 * <li>测试以同一私钥签出不同调用方的 Token（aud=card-service、inst=main），模拟 channel-connector、Payment 与 Platform 经 Platform 签发端取得的 Token。</li>
 * <li>以动态属性开启，独立 Spring 上下文；mode=OFF 的 X-Caller-Service 行为由其他测试类覆盖。</li>
 */
class CardInternalAuthRequiredTest extends AbstractCardApiTest {
    private static final ECKey KEY = generateKey();

    private static final InternalTokenSigner SIGNER = new InternalTokenSigner(KEY);

    @DynamicPropertySource
    static void registerInternalAuth(DynamicPropertyRegistry registry) {
        registry.add("internal-auth.mode", InternalAuthMode.REQUIRED::name);
        registry.add("internal-auth.static-key.private-jwk", KEY::toJSONString);
        registry.add("internal-auth.static-key.public-jwk", () -> KEY.toPublicJWK().toJSONString());
    }

    /** 读卡：channel-connector 的 Token 读到卡数据；Payment、acquiring、Platform 的 Token 404 且无卡数据，票据不被消耗。 */
    @Test
    void test_revealRequiresChannelConnectorToken() {
        String ticket = newTicket();

        int payment = reveal(CardApiConstants.PAYMENT_SERVICE, InternalTokenClaims.GENERAL_PURPOSE, ticket);
        int acquiring = reveal(CardApiConstants.ACQUIRING_SERVICE, InternalTokenClaims.GENERAL_PURPOSE, ticket);
        int platform = reveal(CardApiConstants.PLATFORM_SUPPORT_SERVICE, InternalTokenClaims.GENERAL_PURPOSE, ticket);
        int connector = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, InternalTokenClaims.GENERAL_PURPOSE, ticket);

        assertEquals(List.of(404, 404, 404, 200), List.of(payment, acquiring, platform, connector));
    }

    /** 缺少 Token：401 INTERNAL_TOKEN_INVALID；X-Caller-Service 与 Token 调用方不一致：400；Purpose 不是 GENERAL：404。 */
    @Test
    void test_missingOrMismatchedTokenRejected() {
        String ticket = newTicket();
        String connectorToken = token(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, InternalTokenClaims.GENERAL_PURPOSE);

        int missing = status(null, CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket);
        int mismatched = status(connectorToken, CardApiConstants.PAYMENT_SERVICE, ticket);
        int otherPurpose = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, "CARD_READ", ticket);

        assertEquals(List.of(401, 400, 404), List.of(missing, mismatched, otherPurpose));
        assertEquals(200, status(connectorToken, CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket), "被拒的请求不得消耗票据");
    }

    /** 以 Payment 的 Token 创建工具并签发票据。 */
    String newTicket() {
        String paymentToken = token(CardApiConstants.PAYMENT_SERVICE, InternalTokenClaims.GENERAL_PURPOSE);
        var created = client().post().uri("/card/v1/instruments").header(HttpHeaders.AUTHORIZATION, "Bearer " + paymentToken)
            .header(CardApiConstants.CALLER_HEADER, CardApiConstants.PAYMENT_SERVICE).contentType(MediaType.APPLICATION_JSON)
            .body(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false)).retrieve()
            .body(Map.class);
        var ticket = client().post().uri("/card/v1/material-tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + paymentToken)
            .header(CardApiConstants.CALLER_HEADER, CardApiConstants.PAYMENT_SERVICE).contentType(MediaType.APPLICATION_JSON)
            .body(ticketBody((String)created.get("instrumentRef"), 90)).retrieve().body(Map.class);
        return (String)ticket.get("ticket");
    }

    /** 以给定调用方签出 Token、并以同一调用方作为 X-Caller-Service 读卡，返回 HTTP 状态。 */
    int reveal(String caller, String purpose, String ticket) {
        return status(token(caller, purpose), caller, ticket);
    }

    /** 读卡并返回 HTTP 状态；拒绝时响应不得含卡号。 */
    int status(String token, String headerCaller, String ticket) {
        var request = client().post().uri("/card/v1/channel-card-materials/reveal").contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        request.header(CardApiConstants.CALLER_HEADER, headerCaller);
        try {
            return request.body(Map.of("ticket", ticket)).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException rejected) {
            assertFalse(rejected.getResponseBodyAsString().contains(PAN), "拒绝响应包含卡号");
            return rejected.getStatusCode().value();
        }
    }

    /** 签出指定调用方与 Purpose 的 Token（2 分钟有效）。 */
    static String token(String caller, String purpose) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return SIGNER.sign(new InternalTokenClaims(caller, CardApiConstants.SERVICE_ID, "main", purpose,
            UUID.randomUUID().toString(), now, now.plusSeconds(120)));
    }

    private static ECKey generateKey() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("card-required-test").generate();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
