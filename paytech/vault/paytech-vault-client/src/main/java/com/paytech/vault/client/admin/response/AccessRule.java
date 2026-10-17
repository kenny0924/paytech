package com.paytech.vault.client.admin.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务访问规则。
 */
@Getter
@Setter
public class AccessRule {
    /**
     * 访问规则 ID
     * <li>64 位整数，以字符串传输。</li>
     */
    private String accessRuleId;

    /**
     * 调用服务名
     */
    private String callerService;

    /**
     * 动作
     * <li>PROTECT 或 REVEAL。</li>
     */
    private String action;

    /**
     * 数据类型编码
     */
    private String dataTypeCode;

    /**
     * 访问用途编码
     */
    private String purposeCode;

    /**
     * 状态
     * <li>ACTIVE 或 DISABLED。</li>
     */
    private String status;

    /**
     * 乐观锁版本
     */
    private int version;

    /**
     * 最后更新时间（UTC）
     */
    private Instant updateTime;
}
