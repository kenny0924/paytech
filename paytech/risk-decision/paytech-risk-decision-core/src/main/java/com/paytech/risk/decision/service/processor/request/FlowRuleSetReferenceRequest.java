package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程只读查询输入，由公共 Processor 触发基础校验。 */
@Getter
@AllArgsConstructor
public class FlowRuleSetReferenceRequest {

    /** 真实来源类型。 */
    @NotNull @Pattern(regexp = "FLOW_VERSION|LEGACY_POINT", message = "sourceType 不支持")
    private final String sourceType;

    /** 来源准确版本 ID。 */
    @Positive
    private final long sourceVersionId;

    /** 真实来源中的稳定节点键。 */
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}", message = "nodeKey 格式无效")
    private final String nodeKey;
}
