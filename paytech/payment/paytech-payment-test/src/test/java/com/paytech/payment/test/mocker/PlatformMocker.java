package com.paytech.payment.test.mocker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Platform 读卡的测试替身：与真实 Platform 一样凭渠道输入中的 card.token（card 签发的一次性读卡票据）经真实 HTTP 调用 card 读卡接口
 * （服务发现名 card-service，由 MockHttpServer 以 {@link CardApiMocker} 承载；Caller platform-support-service），由
 * {@link ChannelConnectorMocker} 在渠道操作请求含 card.token 时调用（channel-connector → Platform → card 的组合链路，design/05 §2.1）。
 *
 * <p>
 * 只返回卡号末四位与 CVV 是否存在，以及全部卡值的 SHA-256 摘要（供核对与申请时一致），不保存卡值。
 */
@TestComponent
@RequiredArgsConstructor
public class PlatformMocker {
    private final Environment environment;

    /**
     * 按真实 Platform 的 PAYMENT_CARD 初始化读卡
     *
     * @param input 渠道操作的标准 API 输入
     * @return [“末四位/CVV 是否存在”或 “REJECTED 状态码”, 卡值摘要]；输入不含 card.token 时均为 null
     */
    public String[] revealCard(JsonNode input) {
        if (!input.path("card").path("token").isString()) {
            return new String[2];
        }
        return reveal(input.path("card").path("token").asString());
    }

    /**
     * 以 Platform 身份凭票据调用 card 读卡接口
     *
     * @param ticket 读卡票据
     * @return 同 {@link #revealCard(JsonNode)}
     */
    public String[] reveal(String ticket) {
        var request = new RevealChannelCardMaterialRequestBody();
        request.setTicket(ticket);
        try {
            var card = RestClient.create().post()
                .uri(environment.getRequiredProperty("test.mock-http.base-url") + "/card/v1/channel-card-materials/reveal")
                .header(CardApiConstants.CALLER_HEADER, CardApiConstants.PLATFORM_SUPPORT_SERVICE)
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve()
                .body(RevealChannelCardMaterialResponseBody.class);
            return new String[] {card.getPan().substring(card.getPan().length() - 4) + "/" + (card.getCvv() != null),
                materialDigest(card.getPan(), card.getExpiryMonth(), card.getExpiryYear(), card.getCardholderName(),
                    card.getCvv())};
        } catch (RestClientResponseException rejected) {
            return new String[] {"REJECTED " + rejected.getStatusCode().value(), null};
        }
    }

    /** 卡号、有效期、持卡人与 CVV 的 SHA-256 十六进制摘要；测试用同一方法计算期望值。 */
    public static String materialDigest(String pan, String expiryMonth, String expiryYear, String cardholderName,
        String cvv) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                String.join("|", pan, expiryMonth, expiryYear, cardholderName, cvv).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
