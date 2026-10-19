package com.paytech.channel.connector.api.cost.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本命中请求：六个范围维度加 Channel 与命中时间，均为精确匹配。 */
@Getter
@Setter
public class ResolveChannelCostRequestBody {
    /**
     * Payment Channel 稳定业务 ID
     * <li>选择该 Channel 的唯一方案。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String channelId;

    /** 渠道商户号（本库 t_channel_mid 主键） */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String midId;

    /** CCT Operating Entity 编号 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "必须是两位大写字母加三位数字")
    private String entityNo;

    /** 消费者 ISO 3166-1 alpha-2 国家代码 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "必须是大写 ISO 3166-1 alpha-2 两位代码")
    private String countryCode;

    /** 交易 ISO 4217 币种代码 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /** Payment 支付方式目录代码 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String paymentMethodCode;

    /** Payment 支付、退款或争议动作类别 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String actionType;

    /**
     * 命中时间（UTC）
     * <li>选择 effectiveFrom ≤ asOfTime 且 effectiveTo 为空或晚于 asOfTime 的 ACTIVE / INACTIVE 版本；通常为业务发生时间。</li>
     */
    @NotNull
    private Instant asOfTime;
}
