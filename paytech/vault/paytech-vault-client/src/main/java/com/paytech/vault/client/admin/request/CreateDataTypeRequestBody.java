package com.paytech.vault.client.admin.request;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建数据类型；新建后状态为 ACTIVE，编码与 Token 模式此后不可修改。
 */
@Getter
@Setter
public class CreateDataTypeRequestBody {
    /**
     * 数据类型编码
     * <li>大写字母开头，1..64 位大写字母、数字或下划线。</li>
     */
    private String dataTypeCode;

    /**
     * Token 模式
     * <li>REFERENCE 或 STABLE。</li>
     */
    private String tokenMode;

    /**
     * 数据级别
     * <li>STANDARD 或 SECRET。</li>
     */
    private String dataClass;

    /**
     * 用途说明
     * <li>1..256 个字符，不含受保护值。</li>
     */
    private String description;

    /**
     * 值格式
     * <li>TEXT、JSON 或 REGEX；省略时按 TEXT 处理。</li>
     * <li>TEXT 不限格式；JSON 必须是根为对象且无重复键的 JSON；REGEX 按 valuePattern 整值匹配。</li>
     */
    private String valueFormat;

    /**
     * 正则
     * <li>valueFormat 为 REGEX 时必填，其余格式必须为空。</li>
     * <li>1..256 个字符，Go RE2 语法；Vault 自动包成 ^(?:…)$ 按整值匹配，保存时编译失败即拒绝。</li>
     */
    private String valuePattern;

    /**
     * 测试样例
     * <li>最多 5 个，每个不超过 4096 个字符；只用于本次保存校验，不落库、不进策略变更记录。</li>
     * <li>valueFormat 为 REGEX 时至少 1 个，且必须全部通过正则；其他格式携带时同样按值格式逐个校验。</li>
     */
    private List<String> sampleValues;

    /**
     * 是否允许运营人工加密
     * <li>必填。</li>
     */
    private Boolean manualProtectEnabled;

    /**
     * 是否允许运营人工解密
     * <li>必填。</li>
     */
    private Boolean manualRevealEnabled;

    /**
     * 变更理由
     * <li>必填，1..500 个字符，写入策略变更记录。</li>
     */
    private String reason;
}
