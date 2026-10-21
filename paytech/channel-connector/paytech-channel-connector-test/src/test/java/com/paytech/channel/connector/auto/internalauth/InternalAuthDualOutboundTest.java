package com.paytech.channel.connector.auto.internalauth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.mocker.PaymentCardMocker;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;

/**
 * internal-auth.mode 非 OFF 时渠道协议读卡出站附加内部 Token（2026-10-20 自 Platform InternalAuthDualOutboundTest 迁入读卡部分）
 * <li>真实 card 读卡客户端以 channel-connector-service 身份取得访问 card-service 的 Token：sub 为本服务、aud 为 card-service、用途 GENERAL。</li>
 * <li>channel-connector 没有 Platform 签发通道，本例以 Foundation 本地静态密钥对（仅限本地 / mock）作为 Token 来源并独立验签；
 * 原用例中 Platform 自身签发的 WORKLOAD 审计与 Token 缓存复用两项断言属于 Platform 签发端，随之移除。</li>
 * <li>mode=OFF 不附加 Token 的反向场景由共享上下文的 ExecuteChannelOperationTest、StandardApiCardPaymentTest 覆盖。</li>
 *
 * @see ChannelCardMaterialApi#reveal(String, RevealChannelCardMaterialRequestBody)
 */
class InternalAuthDualOutboundTest extends ChannelConnectorMockTestBase {

    private static final String SELF = "channel-connector-service";
    private static final String INSTANCE = "main";
    private static final String TICKET = "tkt_outbound_000000000000000000000000000001";
    private static final ECKey KEY = generate();

    @Autowired
    private ChannelCardMaterialApi channelCardMaterialApi;

    /** 独立上下文：DUAL 模式、本地静态密钥对与实例 ID。 */
    @DynamicPropertySource
    static void internalAuth(DynamicPropertyRegistry registry) {
        registry.add("internal-auth.mode", InternalAuthMode.DUAL::name);
        registry.add("internal-auth.static-key.private-jwk", KEY::toJSONString);
        registry.add("internal-auth.static-key.public-jwk", () -> KEY.toPublicJWK().toJSONString());
        registry.add("paytech.instance-id", () -> INSTANCE);
    }

    /** 两次读卡都带 Bearer Token；Token 可被 card 按自身服务名验证，调用方为本服务、用途 GENERAL。 */
    @Test
    void test_normalFlow() {
        paymentCard.allow(TICKET, "4111111111111111", "12", "2030", "TEST HOLDER", "123");

        channelCardMaterialApi.reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, request());
        channelCardMaterialApi.reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, request());

        List<String> authorizations = paymentCard.authorizations();
        assertEquals(2, authorizations.size());
        var verifier = new InternalTokenVerifierImpl(
            new StaticKeyInternalTokenSupport(KEY, KEY.toPublicJWK(), SELF, INSTANCE, Clock.systemUTC()),
            CardApiConstants.SERVICE_ID, INSTANCE, Clock.systemUTC());
        for (String authorization : authorizations) {
            assertTrue(authorization.startsWith("Bearer "), "出站必须附加 Bearer Token");
            CallerIdentity identity = verifier.verify(authorization.substring("Bearer ".length()));
            assertAll(() -> assertEquals(SELF, identity.caller()),
                () -> assertEquals(InternalTokenClaims.GENERAL_PURPOSE, identity.purpose()));
        }
        assertEquals(List.of(new PaymentCardMocker.Reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, TICKET),
            new PaymentCardMocker.Reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, TICKET)), paymentCard.reveals());
    }

    /** 最小合法读卡请求。 */
    private static RevealChannelCardMaterialRequestBody request() {
        var body = new RevealChannelCardMaterialRequestBody();
        body.setTicket(TICKET);
        return body;
    }

    /** 本例专用的合成 ES256 密钥对。 */
    private static ECKey generate() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("channel-connector-test").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("无法生成测试 ES256 密钥对", exception);
        }
    }
}
