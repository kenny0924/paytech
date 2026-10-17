package com.paytech.operations.console.bff.service.model.risklist;

import lombok.Getter;
import lombok.Setter;

/**
 * 名单成员；只含脱敏主体键，不含主体原值。
 */
@Getter
@Setter
public class RiskListEntryResponseBody {
    /**
     * 成员 ID
     */
    private String entryId;

    /**
     * 名单 ID
     */
    private String listId;

    /**
     * 主体类型
     */
    private String subjectType;

    /**
     * 脱敏后的规范化主体键
     */
    private String maskedSubjectKey;

    /**
     * 来源
     * <li>MANUAL 人工加入；ACTION 规则动作加入。</li>
     */
    private String sourceType;

    /**
     * 状态
     * <li>ACTIVE、EXPIRED、RELEASED、SUPERSEDED；未知值原样返回。</li>
     */
    private String status;

    /**
     * 生效起点（含），UTC ISO-8601
     */
    private String effectiveFrom;

    /**
     * 生效终点（不含）
     * <li>长期有效时为 null。</li>
     */
    private String effectiveTo;

    /**
     * 加入原因
     */
    private String reason;

    /**
     * 人工添加人账号 ID
     * <li>动作来源为 null。</li>
     */
    private String createdBy;

    /**
     * 失效人账号 ID
     * <li>未失效为 null。</li>
     */
    private String deactivatedBy;

    /**
     * 失效原因
     * <li>未失效为 null。</li>
     */
    private String deactivatedReason;

    /**
     * 创建时间，UTC ISO-8601
     */
    private String createTime;
}
