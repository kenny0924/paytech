package com.paytech.compliance.api.event;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * QualificationChanged Schema v1 的 Payload（事件目录 paytech-compliance-api/event/QualificationChanged/v1）
 * <li>角色资格投影每次状态迁移发布一次：案件决定、运营手工登记与撤销都在同一本地事务登记补偿任务，提交后发布。</li>
 * <li>聚合为角色资格：aggregateType={@link #AGGREGATE_TYPE}、aggregateId=qualificationId、aggregateVersion=qualificationVersion
 * （同一资格连续递增；消费方只按状态做幂等处理，故 strictOrder=false）。</li>
 * <li>client-master 消费 status=REVOKED 自动挂起 Client（Q15）。ID 一律十进制字符串（OPERATING_ENTITY 的 roleId 为 entity_no），时间为 UTC ISO-8601。</li>
 */
@Getter
@Setter
public class QualificationChangedPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "QualificationChanged";

    /** Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型：角色资格投影。 */
    public static final String AGGREGATE_TYPE = "ROLE_QUALIFICATION";

    /** Payload 字段及顺序。 */
    public static final List<String> FIELDS = List.of("qualificationId", "qualificationVersion", "roleType", "roleId",
        "qualificationType", "previousStatus", "status", "decisionId", "decisionSource", "caseId", "validFrom", "validTo",
        "tier", "riskRating", "jurisdictionCode", "responsiblePartnerId", "reasonCode", "changedAt");

    /** 角色资格 ID。 */
    private String qualificationId;

    /** 本次迁移后的资格版本。 */
    private long qualificationVersion;

    /** 角色类型：CLIENT、OPERATING_ENTITY 或 LOCAL_PARTNER。 */
    private String roleType;

    /** 角色 ID。 */
    private String roleId;

    /** 资格类型。 */
    private String qualificationType;

    /** 迁移前状态；首次登记为空。 */
    private String previousStatus;

    /** 迁移后状态：APPROVED、REJECTED、REVOKED 或 EXPIRED。 */
    private String status;

    /** 引起迁移的资格决定 ID。 */
    private String decisionId;

    /** 决定来源：REVIEWER（案件决定）或 MANUAL（运营登记 / 撤销）。 */
    private String decisionSource;

    /** 来源案件 ID；手工决定为空。 */
    private String caseId;

    /** 有效开始时间。 */
    private String validFrom;

    /** 有效结束时间；无截止为空。 */
    private String validTo;

    /** 冻结的尽调档次；手工决定为空。 */
    private String tier;

    /** 冻结的客户风险评级；手工决定或未评级为空。 */
    private String riskRating;

    /** 冻结的辖区代码；手工决定为空。 */
    private String jurisdictionCode;

    /** 冻结的责任 PayFac ID；平台自营或手工决定为空。 */
    private String responsiblePartnerId;

    /** 迁移原因码；可空。 */
    private String reasonCode;

    /** 迁移发生时间。 */
    private String changedAt;
}
