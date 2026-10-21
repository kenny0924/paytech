package com.paytech.reconciliation.api.clearing.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 修改清算账户配置请求：整体替换三项配置与到账银行账户。 */
@Getter
@Setter
public class UpdateClearingAccountSettingsRequestBody {
    /**
     * 到账银行账户主键
     * <li>为空表示解除；非空时必须是同主体、同币种、用途 CHANNEL_SETTLEMENT 的 ACTIVE 银行账户。已生成的预期银行流水不随之改变。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String settlementCashAccountId;

    /**
     * 账单行未命中平台流水的等待天数
     * <li>0–365。</li>
     */
    @NotNull
    @Min(0)
    @Max(365)
    private Integer unmatchedWaitDays;

    /**
     * 实际渠道费与预估之差的容差
     * <li>主货币单位非负十进制字符串，不超过币种精度。</li>
     */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
    private String costToleranceAmount;

    /**
     * 预期银行流水到账宽限天数
     * <li>0–365。</li>
     */
    @NotNull
    @Min(0)
    @Max(365)
    private Integer cashGraceDays;
}
