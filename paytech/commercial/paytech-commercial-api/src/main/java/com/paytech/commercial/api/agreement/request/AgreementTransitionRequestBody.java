package com.paytech.commercial.api.agreement.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 合同状态推进（提交签署、激活）的版本条件。 */
@Getter
@Setter
public class AgreementTransitionRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;
}
