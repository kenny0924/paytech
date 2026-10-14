package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import java.time.Instant;

import lombok.Data;

/** 冻结决策流的执行发布；与结账测试候选共用发布表，以决策流版本非空区分（t_decision_release）。 */
@Data
public class ExecutionRelease {
    /** 发布 ID。 */
    private long releaseId;

    /** 全局唯一的正整数发布编号文本。 */
    private String releaseNo;

    /** 决策点 ID。 */
    private long decisionPointId;

    /** 冻结的决策点版本 ID。 */
    private long pointVersionId;

    /** 冻结的决策流版本 ID；执行发布必填。 */
    private Long decisionFlowVersionId;

    /** 发布环境；一期只有 TEST 可激活。 */
    private DecisionEnvironment environmentCode;

    /** 发布状态。 */
    private DecisionReleaseStatus status;

    /**
     * 生效槽位
     * <li>仅 ACTIVE 为 true，其他为空，保证同决策点同环境至多一个生效发布。</li>
     */
    private Boolean activeSlot;

    /** 发布内容 SHA-256。 */
    private String contentHash;

    /** 创建人账号 ID。 */
    private long createdBy;

    /** 激活 UTC 时间。 */
    private Instant activatedTime;

    /** 被替代 UTC 时间。 */
    private Instant supersededTime;

    /** 并发版本；每次状态变化加一，与状态审计的聚合版本一致。 */
    private int version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
