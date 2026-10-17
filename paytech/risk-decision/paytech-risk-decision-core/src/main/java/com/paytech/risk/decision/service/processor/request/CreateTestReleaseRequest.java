package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布接口的 Owner 输入，由公共模板执行基础校验。 */
@Getter
@AllArgsConstructor
public class CreateTestReleaseRequest {

    /** 可信操作人身份。 */
    @Positive
    private final long actorAccountId;

    /** 准确冻结规则版本。 */
    @Positive
    private final long ruleSetVersionId;

    /** 调用方读取的规则内容哈希。 */
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
    private final String expectedRuleSetContentHash;
}
