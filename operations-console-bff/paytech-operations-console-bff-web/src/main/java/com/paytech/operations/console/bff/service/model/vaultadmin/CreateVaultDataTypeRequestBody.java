package com.paytech.operations.console.bff.service.model.vaultadmin;

import java.util.List;

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
     * 值格式
     * <li>TEXT 不限格式；JSON 必须是 JSON 对象；REGEX 按正则整值匹配。</li>
     * <li>可省略，省略时 Vault 按 TEXT 处理。</li>
     */
    @Pattern(regexp = "TEXT|JSON|REGEX", message = "值格式只能为 TEXT、JSON 或 REGEX")
    private String valueFormat;

    /**
     * 正则
     * <li>仅值格式为 REGEX 时填写；Go RE2 语法，按整值匹配，无需写 ^ $。</li>
     * <li>格式与正则的组合是否合法、能否编译由 Vault 校验。</li>
     */
    @Size(max = 256, message = "正则不超过 256 个字符")
    private String valuePattern;

    /**
     * 测试样例
     * <li>最多 5 个，每个不超过 4096 个字符；只用于本次保存校验，不落库，不写日志。</li>
     * <li>是否必填及是否通过由 Vault 按值格式与正则校验。</li>
     */
    @Size(max = 5, message = "测试样例最多 5 个")
    private List<@NotNull(message = "测试样例不能为空") @Size(max = 4096, message = "测试样例不超过 4096 个字符") String> sampleValues;

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
