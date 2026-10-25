package com.paytech.compliance.service.processor.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一条角色资格的当前状态、实时状态与当前决定来源。 */
@Getter
@AllArgsConstructor
public class QualificationResponse {
    /** 角色资格 ID。 */
    private final long qualificationId;
    /** 角色类型。 */
    private final String roleType;
    /** 角色标识。 */
    private final String roleId;
    /** 资格类型。 */
    private final String qualificationType;
    /** 存储的资格状态。 */
    private final String status;
    /** 查询时实时判定的状态。 */
    private final String effectiveStatus;
    /** 有效开始时间。 */
    private final Instant validFrom;
    /** 有效结束时间；可空。 */
    private final Instant validTo;
    /** 当前决定 ID。 */
    private final long currentDecisionId;
    /** 当前决定来源。 */
    private final String decisionSource;
    /** 复审状态。 */
    private final String reviewStatus;
    /** 并发版本。 */
    private final long version;
    /** 最后更新时间。 */
    private final Instant updatedAt;
}
