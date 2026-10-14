package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;

/**
 * Payment 渠道执行读卡接口的响应 DTO：只返回用例按支付工具 Token 登记的合成测试卡，或按配置返回 Payment 故障；
 * 调用记录只保存调用方、Token、支付尝试与用途，不保存卡值。
 */
@TestComponent
public class PaymentCardMocker implements Clear {

    private final Map<String, RevealChannelCardMaterialResponseBody> cards = new LinkedHashMap<>();
    private final Map<String, Integer> failures = new LinkedHashMap<>();
    private final List<Reveal> reveals = new ArrayList<>();
    private int unexpected;

    /** 登记 Token 对应的合成测试卡。 */
    public synchronized void allow(String token, String pan, String expiryMonth, String expiryYear, String cardholderName,
        String cvv) {
        var card = new RevealChannelCardMaterialResponseBody();
        card.setPan(pan);
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);
        card.setCardholderName(cardholderName);
        card.setCvv(cvv);
        cards.put(token, card);
    }

    /** 登记 Token 的 Payment 故障状态码。 */
    public synchronized void fail(String token, int status) {
        failures.put(token, status);
    }

    /** 由 Payment Mock Controller 调用；未登记 Token 记为意外调用。 */
    public synchronized RevealChannelCardMaterialResponseBody reveal(String caller,
        RevealChannelCardMaterialRequestBody request) {
        String token = request.getPaymentInstrumentToken();
        reveals.add(new Reveal(caller, token, request.getPaymentAttemptId(), request.getPurpose()));
        Integer status = failures.get(token);
        if (status != null) {
            throw MockHttpFault.json(status, Map.of("code", "CARD_NOT_AVAILABLE", "message", "当前不允许用卡"));
        }
        var card = cards.get(token);
        if (card == null) {
            unexpected++;
            throw new IllegalStateException("未配置的读卡请求");
        }
        return card;
    }

    /** 本例读卡记录。 */
    public synchronized List<Reveal> reveals() {
        return List.copyOf(reveals);
    }

    /** 每例结束检查没有未登记的读卡。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 Payment 读卡调用");
    }

    @Override
    public synchronized void clear() {
        cards.clear();
        failures.clear();
        reveals.clear();
        unexpected = 0;
    }

    /** 一次读卡请求的非卡值部分。 */
    public record Reveal(String caller, String token, String paymentAttemptId, String purpose) {
    }
}
