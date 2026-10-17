package com.paytech.risk.control.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.control.core.enums.RiskListEntrySource;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.RiskSubjectType;

import lombok.Data;

/** 风险名单成员（t_risk_list_entry）。 */
@Data
public class RiskListEntry {
    /** 成员 ID。 */
    private long entryId;

    /** 所属名单 ID。 */
    private long listId;

    /** 主体类型，与名单支持类型一致。 */
    private RiskSubjectType subjectType;

    /**
     * 规范化主体键
     * <li>IP、卡指纹属于受限值，不进入日志或响应原文。</li>
     */
    private String subjectKey;

    /** 成员来源。 */
    private RiskListEntrySource sourceType;

    /** 来源 Action Effect ID；仅 ACTION 来源非空。 */
    private Long sourceEffectId;

    /** 人工加入原因；仅 MANUAL 来源非空。 */
    private String reason;

    /** 人工添加人账号 ID；仅 MANUAL 来源非空。 */
    private Long createdBy;

    /** 人工失效人账号 ID；尚未失效或非人工失效时为空。 */
    private Long deactivatedBy;

    /** 失效原因；尚未失效时为空。 */
    private String deactivatedReason;

    /** 成员状态。 */
    private RiskListEntryStatus status;

    /**
     * 当前生效槽位
     * <li>仅 ACTIVE 为 true，其余为空，用于唯一约束保证同名单同主体至多一个生效成员。</li>
     */
    private Boolean activeSlot;

    /** 生效起点 UTC 时间，含。 */
    private Instant effectiveFrom;

    /** 生效终点 UTC 时间，不含；为空表示长期有效。 */
    private Instant effectiveTo;

    /** 被本成员替代的历史成员 ID；一期人工维护不使用替代链。 */
    private Long supersedesEntryId;

    /** 成员并发版本，从 1 开始。 */
    private int version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
