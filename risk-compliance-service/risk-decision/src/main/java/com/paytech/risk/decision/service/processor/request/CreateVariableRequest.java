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
public class CreateVariableRequest {
    /** 可信操作者。 */
    @Positive
    private final long actorId;

    /** 新变量定义与初值。 */
    @NotNull
    private final Create command;

}
