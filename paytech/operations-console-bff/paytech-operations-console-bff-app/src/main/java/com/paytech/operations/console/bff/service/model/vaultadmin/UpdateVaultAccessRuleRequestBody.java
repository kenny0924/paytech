package com.paytech.operations.console.bff.service.model.vaultadmin;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 启用或停用服务访问规则；按乐观锁版本保存。
 */
@Getter
@Setter
public class UpdateVaultAccessRuleRequestBody {
    /**
     * 修改前读取到的版本
     * <li>已被他人修改时返回 409 POLICY_VERSION_CONFLICT。</li>
     */
    @NotNull(message = "版本必填")
    @Min(value = 1, message = "版本必须大于 0")
    private Integer expectedVersion;

    /**
     * 状态
     * <li>ACTIVE 或 DISABLED；只停用不删除。</li>
     */
    @NotNull(message = "状态必填")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "状态只能为 ACTIVE 或 DISABLED")
    private String status;

    /**
     * 变更理由
     * <li>写入 Vault 策略变更记录。</li>
     */
    @NotBlank(message = "变更理由必填")
    @Size(max = 500, message = "变更理由不超过 500 个字符")
    private String reason;
}
