package com.paytech.operations.console.bff.service.model.vaultadmin;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建服务访问规则：调用服务 × 动作 × 数据类型 × 用途。
 */
@Getter
@Setter
public class CreateVaultAccessRuleRequestBody {
    /**
     * 调用服务名
     */
    @NotNull(message = "调用服务必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,127}", message = "调用服务名格式无效")
    private String callerService;

    /**
     * 动作
     */
    @NotNull(message = "动作必填")
    @Pattern(regexp = "PROTECT|REVEAL", message = "动作只能为 PROTECT 或 REVEAL")
    private String action;

    /**
     * 数据类型编码
     * <li>大写字母开头，1..64 位大写字母、数字或下划线。</li>
     */
    @NotNull(message = "数据类型编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "数据类型编码格式无效")
    private String dataTypeCode;

    /**
     * 访问用途编码
     * <li>OPERATIONS_MANUAL 为人工操作保留值，不能用于服务规则。</li>
     */
    @NotNull(message = "访问用途必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "访问用途格式无效")
    private String purposeCode;

    /**
     * 变更理由
     * <li>写入 Vault 策略变更记录。</li>
     */
    @NotBlank(message = "变更理由必填")
    @Size(max = 500, message = "变更理由不超过 500 个字符")
    private String reason;
}
