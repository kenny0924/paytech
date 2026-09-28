package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.flow.DecisionFlow;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程管理内部请求，公共模板在事务和资源锁之前触发字段校验。 */
@Getter
@AllArgsConstructor
public class SaveFlowRequest {
    /** 原始流程配置，由图校验器规范化后保存。 */
    @NotNull
    private final DecisionFlow.Save configuration;
    /** 可信操作者 Account ID。 */
    @Positive
    private final long actorAccountId;
}
