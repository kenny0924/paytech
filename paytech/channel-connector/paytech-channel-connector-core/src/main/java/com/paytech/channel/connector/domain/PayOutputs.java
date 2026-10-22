package com.paytech.channel.connector.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;

/**
 * 标准 API PAY 的输出（PayerMax 卡直付对接技术方案第 4.1 节，草案合同）：包装进程内标准 API 执行返回的输出映射，按字段读取。
 * <li>PAY 输出的字段名与支付状态值只在本类定义（D06：渠道结果映射归 channel-connector）；不存在或不是字符串的字段读作 null。</li>
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

    /** 交互指令中的交互类型 */
    public static final String NEXT_ACTION_TYPE = "type";

    /** 有跳转地址但渠道未给出或给出不认识的类型时使用的交互类型 */
    public static final String REDIRECT = "REDIRECT";

    /** PAY 合同承认的交互类型 */
    static final Set<String> NEXT_ACTION_TYPES = Set.of("THREE_DS_CHALLENGE", REDIRECT);

    /** PAY 合同承认的支付状态；CLOSED、NOT_FOUND 只属于查单 */
    static final Set<ChannelPaymentStatus> PAY_STATUSES =
        Set.of(ChannelPaymentStatus.SUCCEEDED, ChannelPaymentStatus.PENDING, ChannelPaymentStatus.FAILED);

    private final Map<String, Object> values;

    private PayOutputs(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    /** 包装标准 API 输出映射；null 视为空输出。 */
    public static PayOutputs of(Map<String, Object> values) {
        return new PayOutputs(values);
    }

    /** 支付状态：SUCCEEDED、PENDING、FAILED；缺失或不认识的取值为 null，由调用方按结果未知处理。 */
    public ChannelPaymentStatus getPaymentStatus() {
        ChannelPaymentStatus status = paymentStatus(values);
        return status != null && PAY_STATUSES.contains(status) ? status : null;
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

    /**
     * 交互类型：THREE_DS_CHALLENGE 或 REDIRECT
     * <li>没有跳转地址时为 null；渠道未给类型或类型不在合同内时按 REDIRECT。</li>
     */
    public String getNextActionType() {
        if (getRedirectUrl() == null) {
            return null;
        }
        String type = text((Map<?, ?>)values.get(NEXT_ACTION), NEXT_ACTION_TYPE);
        return type != null && NEXT_ACTION_TYPES.contains(type) ? type : REDIRECT;
    }

    /** 输出中出现的顶层字段名，升序；只用于排障，不含值。 */
    public List<String> keys() {
        return List.copyOf(new TreeSet<>(values.keySet()));
    }

    /** 输出中的支付状态代码；缺失或不认识为 null。 */
    static ChannelPaymentStatus paymentStatus(Map<?, ?> values) {
        String status = text(values, PAYMENT_STATUS);
        for (ChannelPaymentStatus candidate : ChannelPaymentStatus.values()) {
            if (candidate.getCode().equals(status)) {
                return candidate;
            }
        }
        return null;
    }

    /** 字符串字段；不存在或类型不符为 null。 */
    static String text(Map<?, ?> map, String name) {
        return map.get(name) instanceof String value ? value : null;
    }

    @Override
    public String toString() {
        return "PayOutputs[" + text(values, PAYMENT_STATUS) + ", values redacted]";
    }
}
