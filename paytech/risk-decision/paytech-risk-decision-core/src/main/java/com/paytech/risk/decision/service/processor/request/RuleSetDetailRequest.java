package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则管理 Owner 输入，基础约束交由公共 Processor 模板验证。 */
@Getter
@AllArgsConstructor
public class RuleSetDetailRequest {
    /** 准确版本 ID。 */
    @Positive
    private final long ruleSetVersionId;
}
