package com.paytech.risk.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 证据引用（t_dd_evidence_ref）；文件由 Platform 持有，摘要固定字节。 */
@Data
public class EvidenceRef {
    /** 证据 ID。 */
    private long evidenceId;

    /** 案件材料要求 ID。 */
    private long requirementId;

    /** 案件主体 ID。 */
    private long caseSubjectId;

    /** Platform 文件 ID。 */
    private long fileId;

    /** 文件 SHA-256。 */
    private String contentHash;

    /** 证据状态：RECEIVED。 */
    private String status;

    /** 并发版本。 */
    private long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
