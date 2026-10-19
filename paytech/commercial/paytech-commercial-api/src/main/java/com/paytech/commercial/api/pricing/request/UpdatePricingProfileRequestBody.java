package com.paytech.commercial.api.pricing.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** DRAFT 内整体改写画像名称、支付方式与条件行；内容不变时不产生变更。 */
@Getter
@Setter
public class UpdatePricingProfileRequestBody {
    /**
     * 调用方读取到的画像 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

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
