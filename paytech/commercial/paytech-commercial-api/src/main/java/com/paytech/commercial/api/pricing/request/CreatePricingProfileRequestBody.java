package com.paytech.commercial.api.pricing.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建 DRAFT 定价画像。
 * <li>pricingProfileCode 是业务唯一身份且不可修改：同码同内容返回原画像，同码不同内容返回 409。</li>
 */
@Getter
@Setter
public class CreatePricingProfileRequestBody {
    /** 全局唯一稳定代码，大写字母开头，1–64 位 */
    @NotBlank(message = "pricingProfileCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "pricingProfileCode 必须是大写稳定代码")
    private String pricingProfileCode;

    /** 定价对象种类，创建后不可修改 */
    @NotBlank(message = "profileKind 必填")
    @Pattern(regexp = "PAYMENT_METHOD|PAYOUT_RAIL|FX_PAIR|DISPUTE|PERIODIC", message = "profileKind 只能为 PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC")
    private String profileKind;

    /** 展示名称，1–128 字符 */
    @NotBlank(message = "profileName 必填")
    @Size(max = 128, message = "profileName 最长 128 字符")
    private String profileName;

    /**
     * Payment Method 代码
     * <li>profileKind=PAYMENT_METHOD 时必填，其他种类必须为空。</li>
     */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "paymentMethodCode 必须是大写稳定代码")
    private String paymentMethodCode;

    /** 条件行，整体提交，最多 50 条，不得重复 */
    @NotNull(message = "conditions 必填，可为空列表")
    @Size(max = 50, message = "conditions 最多 50 条")
    @Valid
    private List<ProfileConditionEntry> conditions;
}
