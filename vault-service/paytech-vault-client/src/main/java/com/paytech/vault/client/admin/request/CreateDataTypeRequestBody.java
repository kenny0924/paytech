package com.paytech.vault.client.admin.request;

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
