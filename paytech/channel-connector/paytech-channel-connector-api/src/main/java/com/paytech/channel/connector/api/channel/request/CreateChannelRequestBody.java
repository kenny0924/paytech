package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建支付渠道请求。 */
@Getter
@Setter
public class CreateChannelRequestBody {
    /**
     * 渠道编码
     * <li>全局唯一，创建后不可修改；须与 Platform 接入方编码一致，渠道激活时核对。</li>
     */
    @NotBlank(message = "渠道编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "渠道编码必须是大写字母开头的大写字母、数字或下划线，2–64 位")
    private String channelCode;

    /** 渠道名称，1–255 字符 */
    @NotBlank(message = "渠道名称必填")
    @Size(max = 255, message = "渠道名称最长 255 字符")
    private String channelName;
}
