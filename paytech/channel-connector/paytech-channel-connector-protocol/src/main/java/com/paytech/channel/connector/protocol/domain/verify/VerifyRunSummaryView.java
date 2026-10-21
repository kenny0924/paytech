package com.paytech.channel.connector.protocol.domain.verify;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.VerifyRunStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 验证运行列表项。 */
@Getter
@AllArgsConstructor
public class VerifyRunSummaryView {

    /** 运行标识。 */
    private final long runId;

    /** 用例编码（运行时快照）。 */
    private final String caseCode;

    /** 用例名称（运行时快照）。 */
    private final String caseName;

    /** 运行状态。 */
    private final VerifyRunStatus runStatus;

    /** 失败摘要。 */
    private final String failureSummary;

    /** 发起账号。 */
    private final Long startedBy;

    /** 开始时间。 */
    private final Instant startTime;

    /** 结束时间。 */
    private final Instant finishTime;
}
