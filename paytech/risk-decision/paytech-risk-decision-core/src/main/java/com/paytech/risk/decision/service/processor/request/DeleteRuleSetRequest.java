package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则管理 Owner 输入，基础约束交由公共 Processor 模板验证。 */
@Getter
@AllArgsConstructor
public class DeleteRuleSetRequest {
    /** 可信操作者。 */
    @Positive
    private final long actorAccountId;

    /** 规则集整体 ID。 */
    @Positive
    private final long ruleSetId;

    /** 编辑者确认的管理版本。 */
    @Positive
    private final long expectedVersion;
}
