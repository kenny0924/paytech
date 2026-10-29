package com.paytech.risk.decision.api.control.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 基于有效 APPROVED 建修订草稿的请求：结论复制自有效版本，输入用最新来源。 */
@Getter
@Setter
public class ReviseUnderwritingRequestBody {

    /** 有效版本的期望并发版本。 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** 修订原因，1—500 字符。 */
    @NotBlank
    @Size(max = 500)
    private String revisionReason;

    /** operations-console-bff 按最新来源重新读取的输入快照。 */
    @NotNull
    @Valid
    private UnderwritingInputBody input;
}
