package com.paytech.payment.execution.core.action;

import com.paytech.channel.connector.api.enums.ChannelOperation;

/**
 * Payment 动作到 channel-connector 渠道操作的唯一映射（design/03 §3：Payment 拥有 ActionType，channel-connector 拥有 ChannelOperation）。
 * <li>只有向渠道发出的动作有对应操作；PAY_RISK 是本地风控动作，没有渠道操作。</li>
 */
public final class ActionChannelOperations {
    private ActionChannelOperations() {
    }

    /**
     * 动作对应的渠道操作
     *
     * @param actionType 渠道动作
     * @return 渠道操作
     * @throws IllegalArgumentException 动作不向渠道发出
     */
    public static ChannelOperation of(ActionType actionType) {
        return switch (actionType) {
            case PAY -> ChannelOperation.PAY;
            case AUTHORIZE -> ChannelOperation.AUTHORIZE;
            case AUTHENTICATE -> ChannelOperation.AUTHENTICATE;
            case CAPTURE -> ChannelOperation.CAPTURE;
            case VOID -> ChannelOperation.VOID;
            case REFUND -> ChannelOperation.REFUND;
            case PAY_RISK -> throw new IllegalArgumentException("PAY_RISK 不是渠道操作");
        };
    }
}
