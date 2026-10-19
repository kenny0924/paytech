package com.paytech.commercial.api.agreement.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 终止 ACTIVE 合同：写入生效终点并登记终止通知文件。 */
@Getter
@Setter
public class TerminateAgreementRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /**
     * 合同生效终点（不含）
     * <li>不得早于当前时间（不回溯收窄），须晚于生效起点且不晚于原终点。</li>
     */
    @NotBlank(message = "effectiveTo 必填")
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})", message = "effectiveTo 必须是带时区偏移的 ISO 8601 时间")
    private String effectiveTo;

    /** 终止通知文件，登记为 TERMINATION_NOTICE */
    @NotNull(message = "terminationNotice 必填")
    @Valid
    private AgreementFileEntry terminationNotice;
}
