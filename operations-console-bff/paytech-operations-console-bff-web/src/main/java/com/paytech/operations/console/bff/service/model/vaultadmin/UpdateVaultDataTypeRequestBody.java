package com.paytech.operations.console.bff.service.model.vaultadmin;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改 Vault 数据类型（不含编码与 Token 模式）；按乐观锁版本保存。
 */
@Getter
@Setter
public class UpdateVaultDataTypeRequestBody {
    /**
     * 修改前读取到的版本
     * <li>已被他人修改时返回 409 POLICY_VERSION_CONFLICT。</li>
     */
    @NotNull(message = "版本必填")
    @Min(value = 1, message = "版本必须大于 0")
    private Integer expectedVersion;

    /**
     * 数据级别
     * <li>STANDARD 普通敏感；SECRET 密钥。</li>
     */
    @NotNull(message = "数据级别必填")
    @Pattern(regexp = "STANDARD|SECRET", message = "数据级别只能为 STANDARD 或 SECRET")
    private String dataClass;

    /**
     * 用途说明
     * <li>不得包含受保护值。</li>
     */
    @NotBlank(message = "用途说明必填")
    @Size(max = 256, message = "用途说明不超过 256 个字符")
    private String description;

    /**
     * 是否允许运营人工加密
     */
    @NotNull(message = "人工加密开关必填")
    private Boolean manualProtectEnabled;

    /**
     * 是否允许运营人工解密
     */
    @NotNull(message = "人工解密开关必填")
    private Boolean manualRevealEnabled;

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
