package com.paytech.risk.decision.domain.query;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本模块查询结果，不对 Web 暴露持久化对象。 */
@Getter
@AllArgsConstructor
public class FlowHistoryItem {

    /** 所选准确版本 ID。 */
    private final long flowVersionId;

    /** 所选版本的业务版本号。 */
    private final int versionNo;

    /** 准确版本保存的流程名称。 */
    private final String flowName;

    /** 所选版本保存人。 */
    private final long createdBy;

    /** 所选版本保存时间。 */
    private final Instant createTime;

    /** 所选版本的变更原因。 */
    private final String changeReason;
}
