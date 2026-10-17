package com.paytech.vault.client.admin.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工加/解密审计记录；不含 Token 与原值。
 */
@Getter
@Setter
public class ManualEvent {
    /**
     * 请求关联标识
     */
    private String requestId;

    /**
     * 操作人账号 ID
     */
    private String actorId;

    /**
     * 动作
     * <li>PROTECT 或 REVEAL。</li>
     */
    private String action;

    /**
     * 数据类型编码
     * <li>请求在识别数据类型前失败时为空串。</li>
     */
    private String dataTypeCode;

    /**
     * 理由码
     */
    private String reasonCode;

    /**
     * 工单号
     */
    private String ticketRef;

    /**
     * 结果
     * <li>SUCCESS、DENIED 或 FAILED。</li>
     */
    private String outcome;

    /**
     * 稳定结果代码
     */
    private String resultCode;

    /**
     * 操作时间（UTC）
     */
    private Instant createTime;
}
