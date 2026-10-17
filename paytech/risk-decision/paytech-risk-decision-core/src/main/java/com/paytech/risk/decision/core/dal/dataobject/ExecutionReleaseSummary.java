package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

import lombok.Data;

/** 执行发布列表的只读投影：发布、所冻结的决策流版本及激活人（t_decision_release 关联决策点、流程版本、流程与状态审计）。 */
@Data
public class ExecutionReleaseSummary {
    /** 发布 ID。 */
    private long releaseId;

    /** 全局唯一的正整数发布编号文本。 */
    private String releaseNo;

    /** 决策点编码。 */
    private String pointCode;

    /** 发布环境。 */
    private DecisionEnvironment environmentCode;

    /** 发布状态。 */
    private DecisionReleaseStatus status;

    /** 决策流 ID。 */
    private long flowId;

    /** 决策流编码。 */
    private String flowCode;

    /** 冻结的决策流版本 ID。 */
    private long flowVersionId;

    /** 决策流版本号。 */
    private int flowVersionNo;

    /** 发布内容摘要。 */
    private String contentHash;

    /** 创建人 Account ID。 */
    private long createdBy;

    /** 创建时间。 */
    private Instant createTime;

    /** 激活人 Account ID，取自进入 ACTIVE 的状态审计；未激活为空。 */
    private Long activatedBy;

    /** 激活时间。 */
    private Instant activatedTime;

    /** 被替代时间。 */
    private Instant supersededTime;
}
