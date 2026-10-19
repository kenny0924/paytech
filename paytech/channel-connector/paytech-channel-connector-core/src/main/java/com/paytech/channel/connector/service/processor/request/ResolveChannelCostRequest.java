package com.paytech.channel.connector.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本命中：Channel、六个范围维度与命中时间，均为精确匹配。 */
@Getter
@Setter
public class ResolveChannelCostRequest {
    /** Payment Channel 主键 */
    @NotNull
    private Long channelId;

    /** 本库 MID 主键 */
    @NotNull
    private Long midId;

    /** Operating Entity 编号 */
    @NotBlank
    private String entityNo;

    /** ISO 3166-1 alpha-2 国家代码 */
    @NotBlank
    private String countryCode;

    /** ISO 4217 币种代码 */
    @NotBlank
    private String currency;

    /** Payment 支付方式目录代码 */
    @NotBlank
    private String paymentMethodCode;

    /** Payment 动作类别 */
    @NotBlank
    private String actionType;

    /** 命中时间（UTC） */
    @NotNull
    private Instant asOfTime;

    /** 错误消息中的全部请求维度。 */
    public String dimensions() {
        return "Channel " + channelId + ", MID " + midId + ", 主体 " + entityNo + ", 国家 " + countryCode + ", 币种 "
            + currency + ", 支付方式 " + paymentMethodCode + ", 动作 " + actionType + ", 命中时间 " + asOfTime;
    }
}
