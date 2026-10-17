package com.paytech.platform.support.integration.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行一次已登记的验证运行（补偿任务入口）。 */
@Getter
@AllArgsConstructor
public class ExecuteVerifyRunRequest {

    /** 运行标识。 */
    private final long runId;

    /** 补偿任务已执行次数；大于 1 表示前一次执行中断。 */
    private final int executeCount;
}
