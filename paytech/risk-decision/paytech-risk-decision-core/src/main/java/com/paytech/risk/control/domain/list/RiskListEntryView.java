package com.paytech.risk.control.domain.list;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;

/**
 * 名单成员的跨模块只读结果；受限主体键只给出脱敏形式
 *
 * @param entryId 成员 ID
 * @param listId 名单 ID
 * @param subjectType 主体类型
 * @param maskedSubjectKey 脱敏后的主体键
 * @param sourceType 来源 Code
 * @param status 状态 Code
 * @param effectiveFrom 生效起点，含
 * @param effectiveTo 生效终点，不含；为空表示长期
 * @param reason 加入原因
 * @param createdBy 人工添加人
 * @param deactivatedBy 人工失效人
 * @param deactivatedReason 失效原因
 * @param createTime 创建时间
 */
public record RiskListEntryView(long entryId, long listId, RiskSubjectType subjectType, String maskedSubjectKey,
    String sourceType, String status, Instant effectiveFrom, Instant effectiveTo, String reason, Long createdBy,
    Long deactivatedBy, String deactivatedReason, Instant createTime) {
    /** 由名单成员构造只读结果，主体键脱敏。 */
    public static RiskListEntryView of(RiskListEntry entry) {
        return new RiskListEntryView(entry.getEntryId(), entry.getListId(), entry.getSubjectType(),
            RiskSubjectKeys.mask(entry.getSubjectType(), entry.getSubjectKey()), entry.getSourceType().getCode(),
            entry.getStatus().getCode(), entry.getEffectiveFrom(), entry.getEffectiveTo(), entry.getReason(),
            entry.getCreatedBy(), entry.getDeactivatedBy(), entry.getDeactivatedReason(), entry.getCreateTime());
    }
}
