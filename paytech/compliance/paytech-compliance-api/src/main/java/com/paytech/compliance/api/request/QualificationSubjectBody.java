package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 要判定资格的一个主体。 */
public class QualificationSubjectBody {

    /** 角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER */
    @NotBlank
    @Pattern(regexp = "^(CLIENT|OPERATING_ENTITY|LOCAL_PARTNER)$")
    private String roleType;

    /**
     * 角色标识
     * <li>OPERATING_ENTITY 为 entity_no（两位大写字母加三位数字，例如 AE001）；CLIENT、LOCAL_PARTNER 为正十进制 ID。</li>
     * <li>格式与角色类型不符时 422 QUALIFICATION_INVALID。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String roleId;

    /** 读取角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER。 */
    public String getRoleType() {
        return roleType;
    }

    /** 设置角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER。 */
    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    /** 读取角色标识。 */
    public String getRoleId() {
        return roleId;
    }

    /** 设置角色标识。 */
    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }
}
