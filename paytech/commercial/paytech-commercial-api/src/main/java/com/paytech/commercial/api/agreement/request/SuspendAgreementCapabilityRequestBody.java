package com.paytech.commercial.api.agreement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 运营手动暂停开通项。 */
@Getter
@Setter
public class SuspendAgreementCapabilityRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /**
     * 暂停原因
     * <li>记入开通项变更快照，最长 200 字。</li>
     */
    @NotBlank(message = "reason 必填")
    @Size(max = 200, message = "reason 最长 200 字")
    private String reason;
}
