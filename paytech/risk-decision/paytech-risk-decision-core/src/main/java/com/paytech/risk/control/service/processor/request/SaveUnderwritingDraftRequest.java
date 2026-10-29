package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存承保草稿的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class SaveUnderwritingDraftRequest {
    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 草稿版本 ID。 */
    @Positive
    private final long underwritingId;

    /** 期望的并发版本。 */
    @Positive
    private final int expectedVersion;

    /** 新的输入快照。 */
    @NotNull
    private final UnderwritingInput input;

    /** 新的结论字段。 */
    @NotNull
    private final UnderwritingTerms terms;
}
