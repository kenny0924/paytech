package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程只读查询输入，由公共 Processor 触发基础校验。 */
@Getter
@AllArgsConstructor
public class FlowListRequest {

    /** 必填决策点编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "pointCode 格式无效")
    private final String pointCode;

    /** 可选流程编码精确匹配。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "flowCode 格式无效")
    private final String flowCode;

    /** 名称包含匹配，不解释 LIKE 通配符。 */
    private final String keyword;

    /** 页码，使用公共分页默认值和范围。 */
    private final Integer page;

    /** 每页条数，使用公共分页默认值和范围。 */
    private final Integer size;
}
