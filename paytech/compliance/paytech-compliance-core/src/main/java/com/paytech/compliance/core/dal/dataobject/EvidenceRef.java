package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Data;

/** 证据引用（t_dd_evidence_ref）；文件由 Platform 持有，地址为 /files/v1/{fileId}，摘要固定字节。 */
@Data
public class EvidenceRef {
    /** 证据 ID。 */
    private long evidenceId;
    /** 案件材料要求 ID。 */
    private long requirementId;
    /** 案件主体 ID。 */
    private long caseSubjectId;
    /** 文件服务地址。 */
    private String fileAddress;
    /** 文件 SHA-256。 */
    private String contentHash;
    /** 材料类型代码。 */
    private String documentType;
    /** 签发日期；可空。 */
    private LocalDate issueDate;
    /** 到期日期；可空。 */
    private LocalDate expiryDate;
    /** 证据状态：RECEIVED、ACCEPTED、REJECTED、SUPERSEDED。 */
    private String status;
    /** 拒绝原因码；非 REJECTED 为空。 */
    private String rejectReasonCode;
    /** 核验备注。 */
    private String reviewNote;
    /** 核验人；未核验为空。 */
    private Long reviewerId;
    /** 核验时间。 */
    private Instant reviewedTime;
    /** 复用来源案件 ID；非复用为空。 */
    private Long reusedFromCaseId;
    /** 并发版本。 */
    private long version;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
