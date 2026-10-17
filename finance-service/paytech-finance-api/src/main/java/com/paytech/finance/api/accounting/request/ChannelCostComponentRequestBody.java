package com.paytech.finance.api.accounting.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本组成：raw = 基数 × 费率 + 固定费，受上下限约束后按币种精度舍入。 */
@Getter
@Setter
public class ChannelCostComponentRequestBody {
    /**
     * 范围内稳定识别成本组成的业务代码
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String componentCode;

    /**
     * 收费语义
     * <li>PROCESSING_FEE、CROSS_BORDER_FEE、REFUND_FEE、DISPUTE_FEE；估算算法对所有类型相同。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String componentType;

    /**
     * 金额计算基数
     * <li>TRANSACTION_AMOUNT：估算调用方传入的交易金额。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String calculationBase;

    /**
     * 十进制费率
     * <li>可为空；与固定费至少一项；取值 [0,1]，最多 10 位小数，例如 "0.029"。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,7})(\\.[0-9]{1,10})?", message = "必须是最多 10 位小数的普通十进制字符串")
    private String rateValue;

    /**
     * 固定费
     * <li>可为空，与 currency 成对；主货币单位十进制字符串，非负且不超过币种精度。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String fixedAmount;

    /**
     * 固定费币种
     * <li>可为空，与 fixedAmount 成对；必须等于适用范围币种（本期不换汇）。</li>
     */
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 计算结果下限
     * <li>可为空；非负且不超过币种精度，与上限同时存在时不大于上限。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String minAmount;

    /**
     * 计算结果上限
     * <li>可为空；非负且不超过币种精度。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String maxAmount;

    /**
     * 归整到币种精度的方式
     * <li>HALF_UP。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String roundingMode;
}
