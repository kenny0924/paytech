package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建 MID 请求；新 MID 为 DRAFT。 */
@Getter
@Setter
public class CreateChannelMidRequestBody {
    /** 所属渠道 ID，正十进制字符串 */
    @NotBlank(message = "渠道 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "渠道 ID 必须是正十进制整数字符串")
    private String channelId;

    /**
     * 绑定的技术账号 ID
     * <li>账号须属于该渠道、未关闭且 OE 与本请求一致。</li>
     */
    @NotBlank(message = "技术账号 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "技术账号 ID 必须是正十进制整数字符串")
    private String channelAccountId;

    /**
     * 渠道分配的商户号
     * <li>1–128 字符；同一渠道内唯一。</li>
     */
    @NotBlank(message = "外部 MID 必填")
    @Size(max = 128, message = "外部 MID 最长 128 字符")
    private String externalMidCode;

    /** 所属 Operating Entity 编号，须与账号一致 */
    @NotBlank(message = "OE 编号必填")
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "OE 编号必须是两位大写字母加三位数字")
    private String entityNo;
}
