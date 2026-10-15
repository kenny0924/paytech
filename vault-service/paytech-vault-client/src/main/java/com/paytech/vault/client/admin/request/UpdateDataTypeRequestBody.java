package com.paytech.vault.client.admin.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改数据类型（不含编码与 Token 模式）；按乐观锁版本更新。
 */
@Getter
@Setter
public class UpdateDataTypeRequestBody {
    /**
     * 修改前读取到的版本
     * <li>与当前版本不一致时返回 POLICY_VERSION_CONFLICT。</li>
     */
    private Integer expectedVersion;

    /**
     * 数据级别
     * <li>STANDARD 或 SECRET。</li>
     */
    private String dataClass;

    /**
     * 用途说明
     * <li>1..256 个字符。</li>
     */
    private String description;

    /**
     * 是否允许运营人工加密
     */
    private Boolean manualProtectEnabled;

    /**
     * 是否允许运营人工解密
     */
    private Boolean manualRevealEnabled;

    /**
     * 状态
     * <li>ACTIVE 或 DISABLED。</li>
     */
    private String status;

    /**
     * 变更理由
     * <li>必填，1..500 个字符，写入策略变更记录。</li>
     */
    private String reason;
}
