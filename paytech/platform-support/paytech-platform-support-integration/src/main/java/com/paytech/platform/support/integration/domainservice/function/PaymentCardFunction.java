package com.paytech.platform.support.integration.domainservice.function;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.payment.api.card.ChannelCardMaterialApi;
import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;
import com.paytech.platform.support.integration.domain.execution.CardValue;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;

import lombok.AllArgsConstructor;

/**
 * PAYMENT_CARD（PayerMax 卡直付对接技术方案第 2、3.1 节）：凭支付工具 Token 与支付尝试 ID 向 Payment 读取卡数据， 结果全部包装为卡值句柄，只在本次调用内存中存在。
 *
 * <p>
 * Token 归属、当前是否允许用卡与 CVV 一次性领取由 Payment Card 判断；本函数只校验返回格式。不缓存、不记录卡值；任何失败只报固定文字， 不带响应原文。在数据库事务外调用。
 */
@Component
@AllArgsConstructor
public class PaymentCardFunction implements InitializerFunction {

    /** 注册函数编码。 */
    public static final String CODE = "PAYMENT_CARD";

    /** 调用方服务名。 */
    public static final String CALLER = "platform-support-service";

    /** 读取用途。 */
    public static final String PURPOSE = "CHANNEL_EXECUTION";

    private static final Pattern PAN = Pattern.compile("[0-9]{12,19}");
    private static final Pattern MONTH = Pattern.compile("0[1-9]|1[0-2]");
    private static final Pattern YEAR = Pattern.compile("[1-9][0-9]{3}");
    private static final Pattern CVV = Pattern.compile("[0-9]{3,4}");
    private static final Pattern NAME = Pattern.compile("[^\\p{Cc}]{1,128}");

    private final ChannelCardMaterialApi cardMaterialApi;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> apply(Map<String, Object> args) {
        var request = new RevealChannelCardMaterialRequestBody();
        request.setPaymentInstrumentToken((String)args.get("paymentInstrumentToken"));
        request.setPaymentAttemptId((String)args.get("paymentAttemptId"));
        request.setPurpose(PURPOSE);
        RevealChannelCardMaterialResponseBody card;
        try {
            card = cardMaterialApi.reveal(CALLER, request);
        } catch (RuntimeException failure) {
            throw new ExecutionErrorException("Payment 读卡失败");
        }
        if (card == null) {
            throw new ExecutionErrorException("Payment 读卡结果为空");
        }
        return values(card.getPan(), card.getExpiryMonth(), card.getExpiryYear(), card.getCardholderName(),
            card.getCvv());
    }

    /**
     * 校验卡数据格式并包装为卡值句柄；cvv 可以为空（不返回该属性）。
     *
     * @return pan、expiryMonth、expiryYear、expiryYearShort（两位年份）、cardholderName、cvv
     */
    public static Map<String, Object> values(String pan, String expiryMonth, String expiryYear, String cardholderName,
        String cvv) {
        if (!matches(PAN, pan) || !matches(MONTH, expiryMonth) || !matches(YEAR, expiryYear)
            || !matches(NAME, cardholderName) || cardholderName.isBlank() || cvv != null && !matches(CVV, cvv)) {
            throw new ExecutionErrorException("卡数据格式无效");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("pan", CardValue.pan(pan));
        result.put("expiryMonth", CardValue.of(expiryMonth));
        result.put("expiryYear", CardValue.of(expiryYear));
        result.put("expiryYearShort", CardValue.of(expiryYear.substring(2)));
        result.put("cardholderName", CardValue.of(cardholderName));
        if (cvv != null) {
            result.put("cvv", CardValue.of(cvv));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }
}
