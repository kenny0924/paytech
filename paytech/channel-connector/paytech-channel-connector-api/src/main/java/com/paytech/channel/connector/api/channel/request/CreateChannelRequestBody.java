package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道请求。 */
@Getter
@Setter
public class CreateChannelRequestBody {
    /**
     * 渠道编码
     * <li>全局唯一，大小写敏感，创建后不可修改；也是外部 API 管理接口中的接入方编码（integrationCode）。</li>
     */
    @NotBlank(message = "渠道编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "渠道编码必须是大写字母开头的大写字母、数字或下划线，2–64 位")
    private String channelCode;

    /** 渠道名称，1–255 字符 */
    @NotBlank(message = "渠道名称必填")
    @Size(max = 255, message = "渠道名称最长 255 字符")
    private String channelName;

    /**
     * 渠道类型
     * <li>PAYMENT 支付渠道：有技术账号（绑定 OE）、MID、能力与成本，只经支付入口执行。</li>
     * <li>SERVICE 服务渠道：最多一份不绑定 OE 的凭据，只经通用入口并按调用授权执行。</li>
     * <li>创建后不可修改。</li>
     */
    @NotBlank(message = "渠道类型必填")
    @Pattern(regexp = "PAYMENT|SERVICE", message = "渠道类型只能为 PAYMENT 或 SERVICE")
    private String channelType;

    /** 渠道描述，可空，最长 512 字符；禁止填写凭据 */
    @Size(max = 512, message = "渠道描述最长 512 字符")
    private String description;
}
