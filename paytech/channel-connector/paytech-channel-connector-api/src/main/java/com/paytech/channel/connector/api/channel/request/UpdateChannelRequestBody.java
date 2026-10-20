package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 修改支付渠道名称请求；渠道编码不可修改。 */
@Getter
@Setter
public class UpdateChannelRequestBody {
    /** 渠道名称，1–255 字符 */
    @NotBlank(message = "渠道名称必填")
    @Size(max = 255, message = "渠道名称最长 255 字符")
    private String channelName;
}
