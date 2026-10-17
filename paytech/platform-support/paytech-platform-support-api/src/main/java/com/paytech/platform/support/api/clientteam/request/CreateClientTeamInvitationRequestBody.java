package com.paytech.platform.support.api.clientteam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发起团队邀请。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateClientTeamInvitationRequestBody {

    /**
     * 受邀邮箱
     * <li>按登录邮箱规则规范化后比较；只出现在请求与邀请邮件中，不回显原值。</li>
     */
    @NotBlank
    @Size(max = 254)
    private String email;

    /** 初始角色：TENANT_ADMIN、TENANT_DEVELOPER 或 TENANT_VIEWER。 */
    @NotBlank
    @Pattern(regexp = "TENANT_ADMIN|TENANT_DEVELOPER|TENANT_VIEWER")
    private String roleCode;

    /** 屏蔽邮箱原值，避免日志或异常打印。 */
    @Override
    public String toString() {
        return "CreateClientTeamInvitationRequestBody[roleCode=" + roleCode + "]";
    }
}
