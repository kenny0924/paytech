package com.paytech.risk.decision.api.control.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 按自动承保规则生成 APPROVED 的请求；结论取规则的标准模板。 */
@Getter
@Setter
public class AutoUnderwritingRequestBody {

    /** operations-console-bff 重新读取的输入快照，生成时冻结。 */
    @NotNull
    @Valid
    private UnderwritingInputBody input;
}
