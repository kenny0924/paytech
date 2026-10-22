package com.paytech.channel.connector.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;

/**
 * 标准 API PAY_QUERY 的输出（3DS 全链路 technique 3.3.2；字段沿用 PayerMax 接入 harness 的 PAY_QUERY 合同）：包装进程内标准 API
 * 执行返回的输出映射，按字段读取。
 * <li>PAY_QUERY 输出的字段名只在本类定义（D06）；支付状态承认 SUCCEEDED、PENDING、FAILED、CLOSED，结论为 FAIL 时另有 NOT_FOUND。</li>
 * <li>金额为交易币种主单位十进制，标准 API 输出可能是字符串或数字；不是合法十进制时读作 null。</li>
 */
public class PayQueryOutputs {
    /** 金额对象 */
    public static final String AMOUNT = "amount";

    /** 金额对象中的主单位十进制值 */
    public static final String AMOUNT_VALUE = "value";

    /** 金额对象中的币种 */
    public static final String AMOUNT_CURRENCY = "currency";

    private final Map<String, Object> values;

    private PayQueryOutputs(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    /** 包装标准 API 输出映射；null 视为空输出。 */
    public static PayQueryOutputs of(Map<String, Object> values) {
        return new PayQueryOutputs(values);
    }

    /** 支付状态：SUCCEEDED、PENDING、FAILED、CLOSED、NOT_FOUND；缺失或不认识的取值为 null。 */
    public ChannelPaymentStatus getPaymentStatus() {
        return PayOutputs.paymentStatus(values);
    }

    /** 渠道交易编号。 */
    public String getChannelTransactionRef() {
        return PayOutputs.text(values, PayOutputs.CHANNEL_TRANSACTION_REF);
    }

    /** 渠道原始状态。 */
    public String getChannelStatus() {
        return PayOutputs.text(values, PayOutputs.CHANNEL_STATUS);
    }

    /** 失败代码。 */
    public String getFailureCode() {
        return PayOutputs.text(values, PayOutputs.FAILURE_CODE);
    }

    /** 失败说明。 */
    public String getFailureMessage() {
        return PayOutputs.text(values, PayOutputs.FAILURE_MESSAGE);
    }

    /** 渠道记录的金额，主单位十进制文本（不舍入、不补零）；未返回或不是合法十进制时为 null。 */
    public String getAmount() {
        Object value = values.get(AMOUNT) instanceof Map<?, ?> amount ? amount.get(AMOUNT_VALUE) : null;
        if (!(value instanceof String) && !(value instanceof Number)) {
            return null;
        }
        try {
            return new BigDecimal(value.toString()).toPlainString();
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** 渠道记录的币种；未返回时为 null。 */
    public String getCurrency() {
        return values.get(AMOUNT) instanceof Map<?, ?> amount ? PayOutputs.text(amount, AMOUNT_CURRENCY) : null;
    }

    /** 输出中出现的顶层字段名，升序；只用于排障，不含值。 */
    public List<String> keys() {
        return List.copyOf(new TreeSet<>(values.keySet()));
    }

    @Override
    public String toString() {
        return "PayQueryOutputs[" + PayOutputs.text(values, PayOutputs.PAYMENT_STATUS) + ", values redacted]";
    }
}
