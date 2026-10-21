package com.paytech.commercial.api.catalog.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * 改写计费事件挂接的开通项种类。
 * <li>capabilityKind 为空表示无需开通的基础产品事件，此时 capabilityCode 必须为空。</li>
 * <li>FEATURE 时 capabilityCode 必填且必须是同产品下的 FEATURE 可开通项；其他种类 capabilityCode 必须为空（任一该种类开通项）。</li>
 */
@Getter
@Setter
public class UpdateFeeEventCapabilityRequestBody {
    /** 调用方读取到的计费事件 changeVersion；不一致返回 409 */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /** 挂接的开通项种类，可空 */
    @Pattern(regexp = "PAYMENT_METHOD|FEATURE|PAYOUT_RAIL|FX|DISPUTE|PERIODIC_SERVICE",
        message = "capabilityKind 只能为 PAYMENT_METHOD、FEATURE、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE")
    private String capabilityKind;

    /** 挂接的功能开通项代码，仅 FEATURE */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "capabilityCode 必须是大写稳定代码")
    private String capabilityCode;
}
