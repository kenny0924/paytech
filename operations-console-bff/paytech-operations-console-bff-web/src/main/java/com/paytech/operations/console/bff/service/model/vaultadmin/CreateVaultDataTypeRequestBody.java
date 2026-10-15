package com.paytech.operations.console.bff.service.model.vaultadmin;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建 Vault 数据类型；保存即生效（Vault 下一次刷新后），编码与 Token 模式此后不可修改。
 */
@Getter
@Setter
public class CreateVaultDataTypeRequestBody {
    /**
     * 数据类型编码
     * <li>大写字母开头，1..64 位大写字母、数字或下划线。</li>
     */
    @NotNull(message = "数据类型编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "数据类型编码格式无效")
    private String dataTypeCode;

    /**
     * Token 模式
     * <li>REFERENCE 每次加密生成新 Token；STABLE 相同原值得到相同 Token。</li>
     */
    @NotNull(message = "Token 模式必填")
    @Pattern(regexp = "REFERENCE|STABLE", message = "Token 模式只能为 REFERENCE 或 STABLE")
    private String tokenMode;

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
     * 变更理由
     * <li>写入 Vault 策略变更记录。</li>
     */
    @NotBlank(message = "变更理由必填")
    @Size(max = 500, message = "变更理由不超过 500 个字符")
    private String reason;
}
