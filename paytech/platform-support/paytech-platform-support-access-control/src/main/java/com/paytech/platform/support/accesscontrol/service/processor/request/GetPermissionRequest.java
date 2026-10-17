package com.paytech.platform.support.accesscontrol.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** GetPermission 的内部应用请求，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class GetPermissionRequest {
    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 不可变权限编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private final String permissionCode;

}
