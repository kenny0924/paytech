package com.paytech.compliance.api.event;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * ClientReviewDecided Schema v2 的 Payload（事件目录 paytech-compliance-api/event/ClientReviewDecided/v2）
 * <li>v2 在 v1 字段之后追加 caseType、roundNo、tier、riskRating、dueTime、errors；outcome 增加 EXPIRED（退回超期）。</li>
 * <li>发布顺序：client-master 先兼容 schemaVersion 1 与 2 并上线，compliance 再发布 v2（technique S-02）。</li>
 * <li>聚合为审核主案件：aggregateType={@link #AGGREGATE_TYPE}、aggregateId=caseId、aggregateVersion=caseVersion（案件版本可能跳号，
 * 故 strictOrder=false）。</li>
 * <li>ID 一律十进制字符串，时间为 UTC ISO-8601；本类只描述合同，不依赖 JSON 库。</li>
 */
@Getter
@Setter
public class ClientReviewDecidedPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "ClientReviewDecided";

    /** Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 2;

    /** 聚合类型：尽调审核主案件。 */
    public static final String AGGREGATE_TYPE = "DD_CASE";

    /** Payload 字段及顺序。 */
    public static final List<String> FIELDS = List.of("caseId", "caseVersion", "applicationId", "submissionId", "outcome",
        "returnSection", "publicNote", "decidedAt", "decisionId", "decidedBy", "caseType", "roundNo", "tier", "riskRating",
        "dueTime", "errors");

    /** 审核主案件 ID。 */
    private String caseId;

    /** 结束后的主案件版本。 */
    private long caseVersion;

    /** Client 申请 ID。 */
    private String applicationId;

    /** 来源提交 ID。 */
    private String submissionId;

    /** 审核结果：RETURNED、APPROVED、REJECTED 或 EXPIRED。 */
    private String outcome;

    /** 兼容字段：退回分区；其他结果或新退回未填写时为空。 */
    private String returnSection;

    /** 客户可见说明；未填写为空。 */
    private String publicNote;

    /** 决定时间（EXPIRED 为过期处理时间），UTC。 */
    private String decidedAt;

    /** 资格决定 ID；RETURNED、EXPIRED 为空。 */
    private String decisionId;

    /** 决定人；系统决定（EXPIRED）为空。 */
    private String decidedBy;

    /** 案件类型，本期固定 ONBOARDING。 */
    private String caseType;

    /** 提交轮次，首次 1。 */
    private int roundNo;

    /** 冻结的尽调档次；APPROVED/REJECTED 填写，其他为空。 */
    private String tier;

    /** 冻结的客户风险评级 LOW、MEDIUM、HIGH；APPROVED/REJECTED 且已评级时填写，其他为空。 */
    private String riskRating;

    /** 退回补件期限，UTC；仅 RETURNED 填写。 */
    private String dueTime;

    /** 退回的需求级错误；非 RETURNED 为空列表。 */
    private List<ClientReviewDecidedErrorPayload> errors;
}
