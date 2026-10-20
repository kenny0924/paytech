package com.paytech.channel.connector.api.channel.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建 MID 能力条件组请求；新组为 ENABLED
 * <li>六维组合（MID、支付方式、体系、国家、币种、MCC）在 MID 内唯一。</li>
 */
@Getter
@Setter
public class CreateMidCapabilityRequestBody {
    /** 所属 MID ID，正十进制字符串 */
    @NotBlank(message = "MID ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "MID ID 必须是正十进制整数字符串")
    private String midId;

    /** 精确支付方式目录代码，不允许通配符 */
    @NotBlank(message = "支付方式必填")
    @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}", message = "支付方式必须是精确目录代码")
    private String paymentMethodCode;

    /** 支付体系代码或通配符 * */
    @NotBlank(message = "支付体系必填")
    @Pattern(regexp = "\\*|[A-Z0-9][A-Z0-9_.-]{0,63}", message = "支付体系必须是目录代码或 *")
    private String schemeCode;

    /** 消费者国家 ISO 3166-1 alpha-2 或通配符 * */
    @NotBlank(message = "国家必填")
    @Pattern(regexp = "\\*|[A-Z]{2}", message = "国家必须是两位大写代码或 *")
    private String countryCode;

    /** 交易币种 ISO 4217 或通配符 * */
    @NotBlank(message = "币种必填")
    @Pattern(regexp = "\\*|[A-Z]{3}", message = "币种必须是三位大写代码或 *")
    private String currency;

    /** 精确四位 MCC，不允许通配符 */
    @NotBlank(message = "MCC 必填")
    @Pattern(regexp = "[0-9]{4}", message = "MCC 必须是四位数字")
    private String mccCode;

    /**
     * 能力明细
     * <li>1–64 项，同一能力代码不能重复。</li>
     */
    @NotEmpty(message = "能力明细至少一项")
    @Size(max = 64, message = "能力明细最多 64 项")
    private List<@NotNull(message = "能力明细不能为空") @Valid MidCapabilityDetailRequestBody> details;
}
