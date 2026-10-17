package com.paytech.payment.execution.domain;

import java.util.Map;

/**
 * 标准 API PAY 的输出（PayerMax 卡直付对接技术方案第 4.1 节，草案合同）：包装 Platform 返回的输出映射，按字段读取。
 * <li>PAY 输出的字段名与支付状态值只在本类定义；不存在或不是字符串的字段读作 null。</li>
 * <li>可能含临时交互指令（3DS 跳转地址），不得持久化。</li>
 */
public class PayOutputs {
    /** 支付状态 */
    public static final String PAYMENT_STATUS = "paymentStatus";

    /** 渠道交易编号 */
    public static final String CHANNEL_TRANSACTION_REF = "channelTransactionRef";

    /** 渠道原始状态 */
    public static final String CHANNEL_STATUS = "channelStatus";

    /** 失败代码 */
    public static final String FAILURE_CODE = "failureCode";

    /** 失败说明 */
    public static final String FAILURE_MESSAGE = "failureMessage";

    /** 交互指令对象 */
    public static final String NEXT_ACTION = "nextAction";

    /** 交互指令中的跳转地址 */
    public static final String REDIRECT_URL = "redirectUrl";

    /** 支付状态：成功 */
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** 支付状态：待认证或处理中 */
    public static final String STATUS_PENDING = "PENDING";

    /** 支付状态：失败 */
    public static final String STATUS_FAILED = "FAILED";

    private final Map<String, Object> values;

    private PayOutputs(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    /** 包装标准 API 输出映射；null 视为空输出。 */
    public static PayOutputs of(Map<String, Object> values) {
        return new PayOutputs(values);
    }

    /** 空输出（未发出请求或结果未知）。 */
    public static PayOutputs empty() {
        return new PayOutputs(Map.of());
    }

    /** 支付状态：SUCCEEDED、PENDING、FAILED。 */
    public String getPaymentStatus() {
        return text(values, PAYMENT_STATUS);
    }

    /** 渠道交易编号。 */
    public String getChannelTransactionRef() {
        return text(values, CHANNEL_TRANSACTION_REF);
    }

    /** 渠道原始状态。 */
    public String getChannelStatus() {
        return text(values, CHANNEL_STATUS);
    }

    /** 失败代码。 */
    public String getFailureCode() {
        return text(values, FAILURE_CODE);
    }

    /** 失败说明。 */
    public String getFailureMessage() {
        return text(values, FAILURE_MESSAGE);
    }

    /** 交互指令中的跳转地址；没有交互指令时为 null。 */
    public String getRedirectUrl() {
        return values.get(NEXT_ACTION) instanceof Map<?, ?> next ? text(next, REDIRECT_URL) : null;
    }

    /** 字符串字段；不存在或类型不符为 null。 */
    static String text(Map<?, ?> map, String name) {
        return map.get(name) instanceof String value ? value : null;
    }

    @Override
    public String toString() {
        return "PayOutputs[" + getPaymentStatus() + ", values redacted]";
    }
}
