package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 修改渠道技术账号的名称与备注；OE 与所属渠道不可修改。 */
@Getter
@Setter
public class UpdateChannelAccountRequestBody {
    /** 账号展示名称，1–128 字符 */
    @NotBlank(message = "账号名称必填")
    @Size(max = 128, message = "账号名称最长 128 字符")
    private String accountName;

    /**
     * 运营备注
     * <li>可空，最长 512 字符；传空表示清除备注。禁止填写凭据。</li>
     */
    @Size(max = 512, message = "备注最长 512 字符")
    private String remark;
}
