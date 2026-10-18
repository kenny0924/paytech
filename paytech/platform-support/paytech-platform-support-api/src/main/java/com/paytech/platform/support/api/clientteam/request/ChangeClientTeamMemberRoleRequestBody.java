package com.paytech.platform.support.api.clientteam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 调整成员角色。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeClientTeamMemberRoleRequestBody {

    /** 新角色：TENANT_ADMIN、TENANT_DEVELOPER 或 TENANT_VIEWER。 */
    @NotBlank
    @Pattern(regexp = "TENANT_ADMIN|TENANT_DEVELOPER|TENANT_VIEWER")
    private String roleCode;
}
