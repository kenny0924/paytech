package com.paytech.operations.console.bff.service.model.riskunderwriting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 页面修订承保：有效版本的期望版本与修订原因；结论由 Risk 复制，输入由 BFF 按最新来源重新读取。 */
@Getter
@Setter
public class ReviseUnderwritingPageRequestBody {
    /** 有效版本的期望并发版本。 */
    @NotNull(message = "期望版本必填")
    @Positive(message = "期望版本须为正整数")
    private Integer expectedVersion;

    /** 修订原因。 */
    @NotBlank(message = "修订原因必填")
    @Size(max = 500, message = "修订原因不超过 500 个字符")
    private String revisionReason;
}
