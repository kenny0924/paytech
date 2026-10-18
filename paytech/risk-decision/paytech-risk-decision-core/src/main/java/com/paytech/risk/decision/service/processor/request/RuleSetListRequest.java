package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则管理 Owner 输入，基础约束交由公共 Processor 模板验证。 */
@Getter
@AllArgsConstructor
public class RuleSetListRequest {
    /** 页码。 */
    @Positive
    private final int page;

    /** 每页条数。 */
    @Min(1) @Max(200)
    private final int size;

    /** 可选决策点编码。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String pointCode;

    /** 可选规则集编码。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String ruleSetCode;

    /** 可选版本状态。 */
    
    private final RuleSetVersionStatus status;
}
