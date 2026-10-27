package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 案件主体的一项检查（t_dd_check）。 */
@Data
public class CaseCheck {
    /** 检查 ID。 */
    private long checkId;
    /** 案件 ID。 */
    private long caseId;
    /** 案件主体 ID。 */
    private long caseSubjectId;
    /** 检查模板 ID。 */
    private long programCheckId;
    /** 执行方式：MANUAL 或 AUTOMATED。 */
    private String checkMode;
    /** 提供方：MANUAL 或服务渠道编码。 */
    private String providerCode;
    /** 请求引用：MANUAL 为检查 ID 文本。 */
    private String requestRef;
    /** 状态：REQUESTED、RUNNING、COMPLETED、FAILED、UNKNOWN。 */
    private String status;
    /** 结论：PASS、FAIL、REVIEW、NOT_EXECUTED；未出结论为空。 */
    private String outcome;
    /** 结论原因码。 */
    private String reasonCode;
    /** 名单版本或查询编号。 */
    private String evidenceRef;
    /** 内部备注。 */
    private String note;
    /** 录入人。 */
    private Long reviewerId;
    /** 录入时间。 */
    private Instant reviewedTime;
    /** 结论有效截止；不设有效期为空。 */
    private Instant validUntil;
    /** 复用来源案件 ID；非复用为空。 */
    private Long reusedFromCaseId;
    /** 操作方。 */
    private String operatedBy;
    /** 代操作的责任 PayFac。 */
    private Long onBehalfOfPartnerId;
    /** 并发版本。 */
    private long version;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
