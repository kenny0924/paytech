package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 修改渠道名称与描述请求；渠道编码与类型不可修改。 */
@Getter
@Setter
public class UpdateChannelRequestBody {
    /** 渠道名称，1–255 字符 */
    @NotBlank(message = "渠道名称必填")
    @Size(max = 255, message = "渠道名称最长 255 字符")
    private String channelName;

    /**
     * 渠道描述
     * <li>整体替换：缺省或空值即清空描述；最长 512 字符，禁止填写凭据。</li>
     */
    @Size(max = 512, message = "渠道描述最长 512 字符")
    private String description;
}
