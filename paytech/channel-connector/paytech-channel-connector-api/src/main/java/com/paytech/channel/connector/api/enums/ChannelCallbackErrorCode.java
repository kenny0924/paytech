package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道支付通知入站的拒绝原因（Problem code，3DS 全链路 technique 3.3.4）
 * <li>消费者是渠道（经 Gateway）：非成功应答使渠道按其重发策略重发，便于排障；通知已入库留痕。</li>
 */
@Getter
@AllArgsConstructor
public enum ChannelCallbackErrorCode implements CodeMessageEnum {
    /** 签名缺失、格式错误或与 Body 不匹配（401）。 */
    CHANNEL_CALLBACK_SIGNATURE_INVALID("CHANNEL_CALLBACK_SIGNATURE_INVALID", "渠道通知验签失败"),

    /** 已验证的内部调用方不是 api-gateway（403）。 */
    CHANNEL_CALLBACK_CALLER_FORBIDDEN("CHANNEL_CALLBACK_CALLER_FORBIDDEN", "渠道通知只接受 Gateway 转发");

    /** 稳定代码 */
    private final String code;

    /** 安全说明 */
    private final String message;
}
