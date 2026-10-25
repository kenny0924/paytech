package com.paytech.compliance.domain;

import com.paytech.compliance.core.enums.QualificationRoleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 要判定资格的主体：角色类型与角色标识；同一角色与标识视为同一主体。 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class QualificationSubject {
    /** 角色类型。 */
    @NotNull
    private final QualificationRoleType roleType;

    /**
     * 角色标识
     * <li>OPERATING_ENTITY 为 entity_no；CLIENT、LOCAL_PARTNER 为十进制 ID 文本。</li>
     */
    @NotBlank
    private final String roleId;

    /** 角色标识是否符合该角色的格式。 */
    public boolean hasValidRoleId() {
        return roleType.acceptsRoleId(roleId);
    }
}
