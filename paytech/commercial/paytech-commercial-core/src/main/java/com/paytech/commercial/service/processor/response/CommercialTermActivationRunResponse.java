package com.paytech.commercial.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮定时激活的结果。 */
@Getter
@AllArgsConstructor
public class CommercialTermActivationRunResponse {
    /** 本轮扫描到的到期版本数 */
    private final int dueCount;

    /** 激活成功数 */
    private final int activatedCount;

    /** 激活失败数（保持 SCHEDULED，下一轮重试） */
    private final int failedCount;
}
