package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则管理 Owner 输入，基础约束交由公共 Processor 模板验证。 */
@Getter
@AllArgsConstructor
public class FreezeRuleSetVersionRequest {
    /** 可信操作者。 */
    @Positive
    private final long actorAccountId;

    /** 准确规则版本 ID。 */
    @Positive
    private final long ruleSetVersionId;

    /** 编辑者读取的内容 Hash。 */
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
    private final String expectedContentHash;
}
