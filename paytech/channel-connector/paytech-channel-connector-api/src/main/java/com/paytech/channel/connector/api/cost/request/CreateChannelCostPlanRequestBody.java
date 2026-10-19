package com.paytech.channel.connector.api.cost.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道成本方案请求。 */
@Getter
@Setter
public class CreateChannelCostPlanRequestBody {
    /**
     * Payment Channel 稳定业务 ID
     * <li>每个 Channel 只能有一个方案；只作匹配条件，不向 Payment 核对。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String channelId;

    /**
     * 跨版本稳定识别方案的业务代码
     * <li>全局唯一。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String planCode;
}
