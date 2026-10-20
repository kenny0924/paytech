package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** MID 改绑技术账号请求；已冻结到支付尝试的三元组不受影响。 */
@Getter
@Setter
public class RebindChannelMidAccountRequestBody {
    /**
     * 目标技术账号 ID
     * <li>须与 MID 同渠道、同 OE 且未关闭。</li>
     */
    @NotBlank(message = "技术账号 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "技术账号 ID 必须是正十进制整数字符串")
    private String channelAccountId;
}
