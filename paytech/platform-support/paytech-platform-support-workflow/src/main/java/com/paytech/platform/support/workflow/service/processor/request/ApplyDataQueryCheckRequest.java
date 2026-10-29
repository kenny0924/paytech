package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;
import com.paytech.platform.support.workflow.domain.runtime.DataQueryCheckOutcome;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 把一次已执行的 DATA_QUERY 检查结果落库的内部请求；只由检查 Processor 在事务外、检查锁内发起。 */
@Getter
@AllArgsConstructor
public class ApplyDataQueryCheckRequest {

    /** 动作运行 ID。 */
    @Positive
    private final long actionRunId;

    /** 所属实例 ID（关系不可变）。 */
    @Positive
    private final long instanceId;

    /** 查询前读到的动作运行版本；落库时版本已变则丢弃结果。 */
    @NotNull
    private final Integer expectedVersion;

    /** 解读后的检查结果。 */
    @NotNull
    private final DataQueryCheckOutcome outcome;

    /** 触发来源；MANUAL 时在事务内重检权限并写 Operations 审计。 */
    @NotNull
    private final DataQueryCheckTrigger trigger;

    /** 可信操作人账号；只有 MANUAL 有值。 */
    private final String actor;

    /** 可信会话；只有 MANUAL 有值。 */
    private final String session;
}
