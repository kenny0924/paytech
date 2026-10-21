package com.paytech.reconciliation.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮等待期满检查的结果。 */
@Getter
@AllArgsConstructor
public class ExpireWaitingChannelLinesResponse {
    /** 本轮转为 CHANNEL_LINE_UNMATCHED 异常的行数 */
    private final int openedCount;

    /** 因并发变化（行已被匹配）跳过的行数 */
    private final int skippedCount;
}
