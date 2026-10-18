package com.paytech.payment.api.execution.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 创建支付意图请求 */
@Getter
@Setter
public class CreateIntentRequestBody {
    /** Client ID */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围")
    private String clientId;

    /**
     * 来源类型
     * <li>当前仅支持 COMMERCE_ORDER。</li>
     */
    @NotBlank(message = "来源类型必填")
    @Pattern(regexp = "COMMERCE_ORDER", message = "来源类型仅支持 COMMERCE_ORDER")
    private String sourceType;

    /**
     * 来源订单 ID
     * <li>与来源类型共同唯一确定 Intent，重复创建沿用原值。</li>
     */
    @NotBlank(message = "来源订单 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "来源订单 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "来源订单 ID 超出有效范围")
    private String sourceId;

    /**
     * 经营主体编号
     * <li>由 Commerce 冻结，例如 AE001；创建后不可修改。</li>
     */
    @NotBlank(message = "经营主体编号必填")
    @Pattern(regexp = "[A-Z]{2}(?!000)[0-9]{3}", message = "经营主体编号必须为两位大写国家代码及 001–999")
    private String entityNo;

    /** 客户定价版本 ID */
    @NotBlank(message = "客户定价版本 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "客户定价版本 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "客户定价版本 ID 超出有效范围")
    private String clientPricingVersionId;

    /**
     * 合作伙伴分成版本 ID
     * <li>可空；与平台服务费版本同时提供或同时省略。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "合作伙伴分成版本 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "合作伙伴分成版本 ID 超出有效范围")
    private String partnerShareVersionId;

    /**
     * 平台服务费版本 ID
     * <li>可空；与合作伙伴分成版本同时提供或同时省略。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "平台服务费版本 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "平台服务费版本 ID 超出有效范围")
    private String platformChargeVersionId;

    /** Commerce 冻结的行业代码 */
    @NotBlank(message = "行业代码必填")
    @Size(max = 64, message = "行业代码最多 64 位")
    private String industryCode;

    /** Commerce 冻结的四位 MCC */
    @NotBlank(message = "MCC 必填")
    @Pattern(regexp = "[0-9]{4}", message = "MCC 必须为四位数字")
    private String mccCode;

    /** Commerce 冻结的消费者国家代码 */
    @NotBlank(message = "消费者国家必填")
    @Pattern(regexp = "[A-Z]{2}", message = "消费者国家必须为两位大写字母")
    private String consumerCountryCode;

    /**
     * 支付金额
     * <li>正十进制字符串，最多 16 位整数和 8 位小数，且符合币种精度。</li>
     */
    @NotBlank(message = "支付金额必填")
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "支付金额格式或精度无效")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    private String amount;

    /** 支付币种，使用三位大写 ISO 4217 代码 */
    @NotBlank(message = "支付币种必填")
    @Pattern(regexp = "[A-Z]{3}", message = "支付币种必须为三位大写字母")
    private String currency;

    /**
     * 支付意图到期时间
     * <li>UTC RFC 3339 字符串，最多微秒，例如 2026-09-30T10:00:00Z。</li>
     * <li>首次创建必须晚于当前时间；重复请求沿用原值。</li>
     */
    @NotBlank(message = "到期时间必填")
    @Pattern(regexp = "[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,6})?Z",
        message = "到期时间必须为 UTC 时间且最多微秒精度")
    private String expiresAt;
}
