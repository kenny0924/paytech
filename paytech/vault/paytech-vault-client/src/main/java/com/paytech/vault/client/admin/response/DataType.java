package com.paytech.vault.client.admin.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Vault 数据类型目录条目。
 */
@Getter
@Setter
public class DataType {
    /**
     * 数据类型编码
     * <li>创建后不可修改。</li>
     */
    private String dataTypeCode;

    /**
     * Token 模式
     * <li>REFERENCE 或 STABLE；创建后不可修改。</li>
     */
    private String tokenMode;

    /**
     * 数据级别
     * <li>STANDARD 普通敏感；SECRET 密钥。</li>
     */
    private String dataClass;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 值格式
     * <li>TEXT 不限格式；JSON 必须是 JSON 对象；REGEX 按 valuePattern 整值匹配。</li>
     * <li>Protect 与人工加密写入时由 Vault 校验，不影响已加密记录的 Reveal。</li>
     */
    private String valueFormat;

    /**
     * 正则
     * <li>Go RE2 语法，按整值匹配；仅 valueFormat 为 REGEX 时出现，其余格式为 null。</li>
     */
    private String valuePattern;

    /**
     * 是否允许运营人工加密
     */
    private boolean manualProtectEnabled;

    /**
     * 是否允许运营人工解密
     */
    private boolean manualRevealEnabled;

    /**
     * 状态
     * <li>ACTIVE 或 DISABLED；只停用不删除。</li>
     */
    private String status;

    /**
     * 乐观锁版本
     * <li>修改时作为 expectedVersion 回传。</li>
     */
    private int version;

    /**
     * 最后更新时间（UTC）
     */
    private Instant updateTime;
}
