package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class UpdateOperationsRoleRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 目标角色。 */
    @Positive
    private final long roleId;

    /** 角色名称。 */
    @NotBlank @Size(max = 255)
    private final String roleName;

    /** 完整权限集合。 */
    @NotNull
    private final List<String> permissionIds;

    /** 角色聚合期望版本。 */
    @Positive
    private final int expectedVersion;

    /** 操作原因。 */
    @NotBlank @Size(max = 500)
    private final String reason;

}
