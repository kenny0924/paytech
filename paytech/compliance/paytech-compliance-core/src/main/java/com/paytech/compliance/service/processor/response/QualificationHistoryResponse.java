package com.paytech.compliance.service.processor.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个角色资格的状态变更及对应决定，按聚合版本倒序。 */
@Getter
@AllArgsConstructor
public class QualificationHistoryResponse {
    /** 角色资格 ID。 */
    private final long qualificationId;
    /** 状态变更，新的在前。 */
    private final List<Item> items;

    /** 一次状态变更及引起它的决定。 */
    @Getter
    @AllArgsConstructor
    public static class Item {
        /** 状态变更 ID。 */
        private final long changeId;
        /** 变更后的资格版本。 */
        private final long aggregateVersion;
        /** 原状态；首次建立为空。 */
        private final String fromStatus;
        /** 新状态。 */
        private final String toStatus;
        /** 触发类型。 */
        private final String triggerType;
        /** 变更原因代码；可空。 */
        private final String reasonCode;
        /** 操作人；回填为空。 */
        private final Long actorId;
        /** 发生时间。 */
        private final Instant occurredAt;
        /** 决定 ID。 */
        private final long decisionId;
        /** 决定来源。 */
        private final String decisionSource;
        /** 决定结论。 */
        private final String outcome;
        /** 决定人；可空。 */
        private final Long decidedBy;
        /** 手工决定的依据说明；其他来源为空。 */
        private final String basisNote;
        /** 决定的有效开始时间。 */
        private final Instant validFrom;
        /** 决定的有效结束时间；可空。 */
        private final Instant validTo;
    }
}
