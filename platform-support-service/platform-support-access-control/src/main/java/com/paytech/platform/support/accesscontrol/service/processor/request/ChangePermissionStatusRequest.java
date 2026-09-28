package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** ChangePermissionStatus 的内部应用请求，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ChangePermissionStatusRequest {
    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 不可变权限编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private final String permissionCode;

    /** 目标权限状态。 */
    @NotNull
    private final PermissionStatus status;

    /** 调用方读取的原版本。 */
    @Positive
    private final int expectedVersion;

    /** 操作原因，最多 500 字符。 */
    @NotBlank @Size(max = 500)
    private final String reason;

}
