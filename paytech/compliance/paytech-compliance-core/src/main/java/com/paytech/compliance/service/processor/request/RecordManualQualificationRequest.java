package com.paytech.compliance.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营手工登记一条已批准的角色资格。 */
@Getter
@AllArgsConstructor
public class RecordManualQualificationRequest {
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** 角色类型。 */
    @NotNull
    private final QualificationRoleType roleType;
    /** 角色标识。 */
    @NotBlank
    private final String roleId;
    /** 资格类型。 */
    @NotNull
    private final QualificationType qualificationType;
    /** 有效开始时间。 */
    @NotNull
    private final Instant validFrom;
    /** 有效结束时间；为空表示没有预定结束时间。 */
    private final Instant validTo;
    /** 登记原因代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private final String reasonCode;
    /** 登记依据说明。 */
    @NotBlank
    @Size(max = 1000)
    private final String basisNote;
    /** 已有资格时页面读取的版本；首次登记为空。 */
    @Positive
    private final Long expectedVersion;
}
