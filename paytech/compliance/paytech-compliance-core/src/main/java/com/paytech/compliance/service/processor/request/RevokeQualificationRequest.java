package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营撤销一条当前已批准的角色资格。 */
@Getter
@AllArgsConstructor
public class RevokeQualificationRequest {
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** 角色资格 ID。 */
    @Positive
    private final long qualificationId;
    /** 页面读取的资格版本。 */
    @Positive
    private final long expectedVersion;
    /** 撤销原因代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private final String reasonCode;
    /** 撤销依据说明。 */
    @NotBlank
    @Size(max = 1000)
    private final String basisNote;
}
