package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 一次退回登记的一项需求级错误（t_dd_return_error）。 */
@Data
public class ReturnError {
    /** 错误 ID。 */
    private long returnErrorId;
    /** 被退回的主案件 ID。 */
    private long caseId;
    /** 案件内序号，从 1 起。 */
    private int sequenceNo;
    /** 需求键。 */
    private String requirementKey;
    /** 主体 Party ID；与主体无关为空。 */
    private Long subjectPartyId;
    /** 错误码。 */
    private String errorCode;
    /** 给 Client 的说明。 */
    private String reason;
    /** 补救动作码。 */
    private String remediationCode;
    /** 创建 UTC 时间。 */
    private Instant createTime;
}
