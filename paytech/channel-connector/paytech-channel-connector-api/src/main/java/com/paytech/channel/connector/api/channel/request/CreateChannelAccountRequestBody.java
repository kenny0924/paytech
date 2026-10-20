package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道技术账号请求；新账号为 DRAFT 且尚无配置。 */
@Getter
@Setter
public class CreateChannelAccountRequestBody {
    /** 所属渠道 ID，正十进制字符串 */
    @NotBlank(message = "渠道 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "渠道 ID 必须是正十进制整数字符串")
    private String channelId;

    /**
     * 所属 Operating Entity 编号
     * <li>两位大写国家代码加三位数字，如 AE001；本期只校验格式，不向 CCT 核对存在性。创建后不可修改。</li>
     */
    @NotBlank(message = "OE 编号必填")
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "OE 编号必须是两位大写字母加三位数字")
    private String entityNo;

    /** 账号展示名称，1–128 字符，不唯一 */
    @NotBlank(message = "账号名称必填")
    @Size(max = 128, message = "账号名称最长 128 字符")
    private String accountName;

    /**
     * 运营备注
     * <li>可空，最长 512 字符；禁止填写凭据。</li>
     */
    @Size(max = 512, message = "备注最长 512 字符")
    private String remark;
}
