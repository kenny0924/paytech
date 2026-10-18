package com.paytech.vault.client.admin.request;

import java.util.List;

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
     * 值格式
     * <li>TEXT、JSON 或 REGEX。</li>
     * <li>省略时值格式与正则都保持不变；携带时与 valuePattern 一起整体替换。</li>
     * <li>只对之后的写入生效，已加密记录不重新校验。</li>
     */
    private String valueFormat;

    /**
     * 正则
     * <li>结果为 REGEX 时必填，其余格式必须为空；valueFormat 省略时忽略本字段。</li>
     * <li>1..256 个字符，Go RE2 语法；Vault 自动包成 ^(?:…)$ 按整值匹配，保存时编译失败即拒绝。</li>
     */
    private String valuePattern;

    /**
     * 测试样例
     * <li>最多 5 个，每个不超过 4096 个字符；只用于本次保存校验，不落库、不进策略变更记录。</li>
     * <li>结果为 REGEX 且从其他格式切换过来或正则有变化时至少 1 个，且必须全部通过正则。</li>
     * <li>其他情况可省略；携带时同样按结果值格式逐个校验。</li>
     */
    private List<String> sampleValues;

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
