package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.domain.underwriting.UnderwritingInput;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按自动承保规则生成 APPROVED 的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class AutoUnderwritingRequest {
    /** 触发人账号 ID，记录为创建人。 */
    private final long actorAccountId;

    /** 被承保 Client ID。 */
    @Positive
    private final long clientId;

    /** 生成时冻结的输入快照。 */
    @NotNull
    private final UnderwritingInput input;
}
