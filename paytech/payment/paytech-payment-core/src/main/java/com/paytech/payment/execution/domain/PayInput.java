package com.paytech.payment.execution.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 标准 API PAY 的输入（PayerMax 卡直付对接技术方案第 4.1 节，草案合同；3DS 全链路 technique 3.3.7 增加 threeDsRequired 与 notifyUrl）：
 * 不含卡值与账号材料，卡只以 card 签发的一次性读卡票据传递（design/05 §2.1）。
 * <li>PAY 输入的字段名只在本类定义；channelAccount 由 Channel 按冻结账号补齐，不在此处。</li>
 * <li>回跳地址、付款人引用与网络信息只用于本次渠道请求，不持久化。</li>
 */
@Getter
@Setter
public class PayInput {
    /** 卡对象 */
    public static final String CARD = "card";

    /** 卡对象中的卡字段句柄（一次性读卡票据） */
    public static final String CARD_TOKEN = "token";

    /** 支付对象 */
    public static final String PAYMENT = "payment";

    /** 支付尝试 ID，即渠道订单号 */
    public static final String ATTEMPT_ID = "attemptId";

    /** 金额对象 */
    public static final String AMOUNT = "amount";

    /** 金额对象中的主单位十进制字符串 */
    public static final String AMOUNT_VALUE = "value";

    /** 金额对象中的币种 */
    public static final String AMOUNT_CURRENCY = "currency";

    /** 消费者国家 */
    public static final String COUNTRY = "country";

    /** 交互完成后的回跳地址 */
    public static final String RETURN_URL = "returnUrl";

    /** 付款人引用 */
    public static final String PAYER_REFERENCE = "payerReference";

    /** 消费者 IP 地址 */
    public static final String CLIENT_IP = "clientIp";

    /** 消费者浏览器 User-Agent */
    public static final String USER_AGENT = "userAgent";

    /** 风控要求本次支付做 3DS */
    public static final String THREE_DS_REQUIRED = "threeDsRequired";

    /** 渠道异步通知地址 */
    public static final String NOTIFY_URL = "notifyUrl";

    /** 卡字段句柄：card 签发的一次性读卡票据 */
    private String cardToken;

    /** 支付尝试 ID */
    private String attemptId;

    /** 金额，按币种精度的主单位十进制字符串 */
    private String amount;

    /** 币种 */
    private String currency;

    /** 消费者国家 */
    private String country;

    /** 回跳地址 */
    private String returnUrl;

    /** 不含个人信息的付款人引用 */
    private String payerReference;

    /** 消费者 IP 地址，可为空 */
    private String clientIp;

    /** 消费者浏览器 User-Agent，可为空 */
    private String userAgent;

    /**
     * 风控是否要求本次支付做 3DS
     * <li>只在要求时写入 payment.threeDsRequired=true；不要求时省略，避免未声明该字段的旧版标准 API 拒绝请求。</li>
     */
    private boolean threeDsRequired;

    /** 渠道异步通知地址；未配置通知入口时为空并省略 */
    private String notifyUrl;

    /** 转为标准 API 输入的嵌套结构；可选字段为空时省略。 */
    public Map<String, Object> toMap() {
        var payment = new LinkedHashMap<String, Object>();
        payment.put(ATTEMPT_ID, attemptId);
        payment.put(AMOUNT, Map.of(AMOUNT_VALUE, amount, AMOUNT_CURRENCY, currency));
        payment.put(COUNTRY, country);
        payment.put(RETURN_URL, returnUrl);
        payment.put(PAYER_REFERENCE, payerReference);
        if (clientIp != null) {
            payment.put(CLIENT_IP, clientIp);
        }
        if (userAgent != null) {
            payment.put(USER_AGENT, userAgent);
        }
        if (threeDsRequired) {
            payment.put(THREE_DS_REQUIRED, true);
        }
        if (notifyUrl != null) {
            payment.put(NOTIFY_URL, notifyUrl);
        }
        var input = new LinkedHashMap<String, Object>();
        input.put(CARD, Map.of(CARD_TOKEN, cardToken));
        input.put(PAYMENT, payment);
        return input;
    }

    @Override
    public String toString() {
        return "PayInput[" + attemptId + ", values redacted]";
    }
}
