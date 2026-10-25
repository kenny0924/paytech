package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.QualificationType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 角色资格列表查询；筛选条件均可空，roleId 必须与 roleType 一起提供。 */
@Getter
@AllArgsConstructor
public class ListQualificationsRequest {
    /** 页码，从 1 开始。 */
    @Positive
    private final int page;
    /** 每页条数。 */
    @Positive
    private final int size;
    /** 数据库偏移量。 */
    private final int offset;
    /** 角色类型。 */
    private final QualificationRoleType roleType;
    /** 角色标识。 */
    private final String roleId;
    /** 资格类型。 */
    private final QualificationType qualificationType;
    /** 存储的资格状态。 */
    private final QualificationStatus status;
}
