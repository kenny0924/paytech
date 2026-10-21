package com.paytech.channel.connector.protocol.domain.verify;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.VerifyRunStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用例最近一次运行的摘要。 */
@Getter
@AllArgsConstructor
public class VerifyRunBriefView {

    /** 运行标识。 */
    private final long runId;

    /** 运行状态。 */
    private final VerifyRunStatus runStatus;

    /** 开始时间。 */
    private final Instant startTime;

    /** 结束时间；运行中为空。 */
    private final Instant finishTime;
}
