package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class CreateOperationsRoleRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 不可变角色代码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String roleCode;

    /** 角色名称。 */
    @NotBlank @Size(max = 255)
    private final String roleName;

    /** 完整权限集合，可为空列表。 */
    @NotNull
    private final List<String> permissionIds;

    /** 操作原因。 */
    @NotBlank @Size(max = 500)
    private final String reason;

}
