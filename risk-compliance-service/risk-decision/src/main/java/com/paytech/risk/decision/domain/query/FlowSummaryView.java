package com.paytech.risk.decision.domain.query;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本模块查询结果，不对 Web 暴露持久化对象。 */
@Getter
@AllArgsConstructor
public class FlowSummaryView {

    /** 流程管理根 ID。 */
    private final long flowId;

    /** 所属决策点编码。 */
    private final String pointCode;

    /** 流程永久编码。 */
    private final String flowCode;

    /** 准确版本保存的流程名称。 */
    private final String flowName;

    /** 当前配置版本 ID。 */
    private final long currentFlowVersionId;

    /** 当前最高业务版本号。 */
    private final int currentVersionNo;

    /** 管理根修订号。 */
    private final long version;

    /** 准确版本中的规则集节点数。 */
    private final int ruleSetNodeCount;

    /** 当前版本保存人。 */
    private final long updatedBy;

    /** 管理根更新时间。 */
    private final Instant updateTime;
}
