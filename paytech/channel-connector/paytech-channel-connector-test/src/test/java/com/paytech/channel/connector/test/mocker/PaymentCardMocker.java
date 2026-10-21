package com.paytech.channel.connector.test.mocker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * card-service 凭票据读卡接口的响应 DTO（2026-10-20 随渠道协议自 Platform 迁入，调用方改为 channel-connector-service）
 * <li>只返回用例按读卡票据登记的合成测试卡，或按配置返回 card 故障。</li>
 * <li>调用记录只保存调用方与票据，不保存卡值。</li>
 */
@TestComponent
public class PaymentCardMocker implements Clear {

    /** 读卡请求未携带 Authorization 头。 */
    public static final String NO_AUTHORIZATION = "<none>";

    private final Map<String, RevealChannelCardMaterialResponseBody> cards = new LinkedHashMap<>();
    private final Map<String, Integer> failures = new LinkedHashMap<>();
    private final List<Reveal> reveals = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private int unexpected;

    /**
     * 登记票据对应的合成测试卡
     *
     * @param ticket 一次性读卡票据
     * @param pan 卡号
     * @param expiryMonth 有效期月
     * @param expiryYear 有效期年
     * @param cardholderName 持卡人
     * @param cvv CVV
     */
    public synchronized void allow(String ticket, String pan, String expiryMonth, String expiryYear,
        String cardholderName, String cvv) {
        var card = new RevealChannelCardMaterialResponseBody();
        card.setPan(pan);
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);
        card.setCardholderName(cardholderName);
        card.setCvv(cvv);
        cards.put(ticket, card);
    }

    /**
     * 登记票据的 card 故障状态码
     *
     * @param ticket 一次性读卡票据
     * @param status HTTP 状态码
     */
    public synchronized void fail(String ticket, int status) {
        failures.put(ticket, status);
    }

    /**
     * 由 card Mock Controller 调用；未登记票据记为意外调用
     *
     * @param caller 调用方服务名
     * @param request 读卡请求
     * @return 登记的合成测试卡
     */
    public synchronized RevealChannelCardMaterialResponseBody reveal(String caller,
        RevealChannelCardMaterialRequestBody request) {
        String ticket = request.getTicket();
        reveals.add(new Reveal(caller, ticket));
        Integer status = failures.get(ticket);
        if (status != null) {
            throw MockHttpFault.json(status, Map.of("code", "CARD_NOT_AVAILABLE", "message", "当前不允许用卡"));
        }
        var card = cards.get(ticket);
        if (card == null) {
            unexpected++;
            throw new IllegalStateException("未配置的读卡请求");
        }
        return card;
    }

    /**
     * 由 card Mock Controller 记录每次读卡请求的 Authorization 头
     *
     * @param authorization 请求头原值；没有该头时记为 {@link #NO_AUTHORIZATION}
     */
    public synchronized void recordAuthorization(String authorization) {
        authorizations.add(authorization == null ? NO_AUTHORIZATION : authorization);
    }

    /** 本例读卡请求的 Authorization 头（内部 Token 只在内存中保留到本例结束）。 */
    public synchronized List<String> authorizations() {
        return List.copyOf(authorizations);
    }

    /** 本例读卡记录。 */
    public synchronized List<Reveal> reveals() {
        return List.copyOf(reveals);
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(cards.isEmpty() && failures.isEmpty() && reveals.isEmpty() && authorizations.isEmpty()
            && unexpected == 0, "card Mock 未从空状态开始");
    }

    /** 每例结束检查没有未登记的读卡。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 card 读卡调用");
    }

    /** 由 Foundation 的 AfterEach 统一回收登记的卡与调用记录。 */
    @Override
    public synchronized void clear() {
        cards.clear();
        failures.clear();
        reveals.clear();
        authorizations.clear();
        unexpected = 0;
    }

    /**
     * 一次读卡请求的非卡值部分。
     *
     * @param caller 调用方服务名
     * @param ticket 读卡票据
     */
    public record Reveal(String caller, String ticket) {
    }
}
