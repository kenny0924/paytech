package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.flow.DecisionFlow;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程管理内部请求，公共模板在事务和资源锁之前触发字段校验。 */
@Getter
@AllArgsConstructor
public class ValidateFlowRequest {
    /** 待检查配置；合法但不完整的图返回定位问题。 */
    @NotNull
    private final DecisionFlow.Save configuration;
}
