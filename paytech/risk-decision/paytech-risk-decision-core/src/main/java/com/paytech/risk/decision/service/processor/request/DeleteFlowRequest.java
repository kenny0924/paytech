package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程管理内部请求，公共模板在事务和资源锁之前触发字段校验。 */
@Getter
@AllArgsConstructor
public class DeleteFlowRequest {
    /** 原请求中的流程管理身份。 */
    @Positive
    private final long flowId;
    /** 调用方最后读取的根修订号。 */
    @Positive
    private final long expectedVersion;
    /** 可信操作者 Account ID。 */
    @Positive
    private final long actorAccountId;
}
