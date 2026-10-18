package com.paytech.platform.support.integration.domain.verify;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.VerifyRunStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 验证运行详情与证据。 */
@Getter
@AllArgsConstructor
public class VerifyRunDetailView {

    /** 运行标识。 */
    private final long runId;

    /** 用例编码（运行时快照）。 */
    private final String caseCode;

    /** 运行状态。 */
    private final VerifyRunStatus runStatus;

    /** 失败摘要。 */
    private final String failureSummary;

    /** 运行时的用例快照。 */
    private final JsonNode caseSnapshot;

    /** 运行时的配置版本。 */
    private final JsonNode configVersions;

    /** 运行时的出站地址。 */
    private final JsonNode endpoints;

    /** 运行后用例、账号或引用配置是否已变更。 */
    private final boolean stale;

    /** 发起账号。 */
    private final Long startedBy;

    /** 开始时间。 */
    private final Instant startTime;

    /** 结束时间。 */
    private final Instant finishTime;

    /** 步骤。 */
    private final List<VerifyRunStepView> steps;
}
