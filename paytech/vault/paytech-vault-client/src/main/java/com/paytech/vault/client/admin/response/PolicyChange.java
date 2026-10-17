package com.paytech.vault.client.admin.response;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/**
 * 策略变更记录；列表不含快照，详情含变更前后快照。
 */
@Getter
@Setter
public class PolicyChange {
    /**
     * 变更后的策略版本号
     */
    private int policyVersionNo;

    /**
     * 变更类型
     * <li>SEED、DATA_TYPE_CREATE、DATA_TYPE_UPDATE、RULE_CREATE 或 RULE_UPDATE。</li>
     */
    private String changeType;

    /**
     * 对象类型
     * <li>POLICY、DATA_TYPE 或 ACCESS_RULE。</li>
     */
    private String targetType;

    /**
     * 对象标识
     * <li>数据类型编码或规则四元组。</li>
     */
    private String targetKey;

    /**
     * 操作人账号 ID
     * <li>初始化数据为 SYSTEM。</li>
     */
    private String actorId;

    /**
     * 变更理由
     */
    private String reason;

    /**
     * 变更时间（UTC）
     */
    private Instant createTime;

    /**
     * 变更前对象元数据
     * <li>新建或列表结果时为空；不含任何受保护值。</li>
     */
    private JsonNode beforeSnapshot;

    /**
     * 变更后对象元数据
     * <li>列表结果时为空；不含任何受保护值。</li>
     */
    private JsonNode afterSnapshot;
}
