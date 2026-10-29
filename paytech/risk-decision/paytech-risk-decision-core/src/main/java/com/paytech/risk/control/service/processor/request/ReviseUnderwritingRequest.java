package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.domain.underwriting.UnderwritingInput;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 基于有效 APPROVED 建修订草稿的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class ReviseUnderwritingRequest {
    /** 操作人账号 ID，记录为创建人。 */
    private final long actorAccountId;

    /** 当前有效版本 ID。 */
    @Positive
    private final long underwritingId;

    /** 有效版本的期望并发版本。 */
    @Positive
    private final int expectedVersion;

    /** 修订原因。 */
    @NotBlank
    @Size(max = 500)
    private final String revisionReason;

    /** 按最新来源取得的输入快照。 */
    @NotNull
    private final UnderwritingInput input;
}
