package com.paytech.platform.support.auto.ext.http.internalauth;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.platform.support.integration.domainservice.function.PaymentCardFunction;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * internal-auth.mode 非 OFF 时 Platform 出站附加内部 Token：真实 card 读卡客户端经进程内签发通道取得访问 card-service 的 Token。
 * <li>Token 的 sub 为 Platform 自身、aud 为 card-service；自身签发同样写 WORKLOAD 审计；同一目标在有效期内复用缓存的 Token。</li>
 * <li>mode=OFF 不附加 Token 的反向场景由共享上下文的 StandardApiCardPaymentTest 覆盖。</li>
 *
 * @see ChannelCardMaterialApi#reveal(String, RevealChannelCardMaterialRequestBody)
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
class InternalAuthDualOutboundTest extends MockTestBase {

    private static final String SELF = "platform-support-service";
    private static final String CARD = "card-service";
    private static final String INSTRUMENT = "tkt_outbound_000000000000000000000000000001";

    @Autowired
    InternalTokens tokens;

    @Autowired
    AuditTestMapper auditData;

    @Autowired
    ChannelCardMaterialApi channelCardMaterialApi;

    /** 两次读卡都带 Bearer Token；Token 可被 card 按自身服务名验证；只签发一次。 */
    @Test
    void test_normalFlow() {
        paymentCard.allow(INSTRUMENT, "4111111111111111", "12", "30", "TEST HOLDER", "123");

        channelCardMaterialApi.reveal(PaymentCardFunction.CALLER, request());
        channelCardMaterialApi.reveal(PaymentCardFunction.CALLER, request());

        List<String> authorizations = paymentCard.authorizations();
        assertEquals(2, authorizations.size());
        assertTrue(authorizations.getFirst().startsWith("Bearer "), "出站必须附加 Bearer Token");
        String token = authorizations.getFirst().substring("Bearer ".length());
        var identity = tokens.verifyAs(token, CARD);
        assertAll(() -> assertEquals(SELF, identity.caller()),
            () -> assertEquals("GENERAL", identity.purpose()),
            () -> assertEquals(authorizations.getFirst(), authorizations.get(1), "有效期内复用缓存的 Token"),
            () -> assertEquals(1, auditData.eventCount(), "缓存命中不重复签发"));
    }

    /** 最小合法读卡请求。 */
    private static RevealChannelCardMaterialRequestBody request() {
        var body = new RevealChannelCardMaterialRequestBody();
        body.setTicket(INSTRUMENT);
        return body;
    }
}
