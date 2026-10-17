package com.paytech.vault.client.admin.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 启用或停用访问规则；按乐观锁版本更新。
 */
@Getter
@Setter
public class UpdateAccessRuleRequestBody {
    /**
     * 修改前读取到的版本
     */
    private Integer expectedVersion;

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
