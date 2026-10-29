package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 建承保草稿的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class CreateUnderwritingDraftRequest {
    /** 操作人账号 ID，记录为创建人。 */
    private final long actorAccountId;

    /** 被承保 Client ID。 */
    @Positive
    private final long clientId;

    /** 输入快照。 */
    @NotNull
    private final UnderwritingInput input;

    /** 初始结论字段；未填写时为空结论。 */
    @NotNull
    private final UnderwritingTerms terms;
}
