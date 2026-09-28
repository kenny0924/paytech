package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 变量 Owner 输入，公共 Processor 模板触发基础校验。 */
@Getter
@AllArgsConstructor
public class VariableListRequest {
    /** 字面搜索关键字。 */
    private final String keyword;

    /** 可选来源类型。 */
    private final String sourceType;

    /** 可选值类型。 */
    private final String valueType;

    /** 可选状态。 */
    private final String status;

    /** 可选决策点。 */
    private final String pointCode;

    /** 可选引用用途。 */
    private final String usage;

    /** 可选节点类型。 */
    private final String nodeType;

    /** 逗号分隔批取编码。 */
    private final String codes;

    /** 可选页码，批取不可携带。 */
    private final Integer page;

    /** 可选页大小，批取不可携带。 */
    private final Integer size;

}
