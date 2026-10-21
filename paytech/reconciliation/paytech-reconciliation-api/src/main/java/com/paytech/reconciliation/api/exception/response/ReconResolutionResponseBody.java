package com.paytech.reconciliation.api.exception.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 异常处置与复核结果。 */
@Getter
@Setter
public class ReconResolutionResponseBody {
    /** 处置主键 */
    private String resolutionId;

    /** 异常主键 */
    private String exceptionId;

    /** 同一异常内从 1 起的处置序号 */
    private Integer resolutionNo;

    /** 处置方式：RE_MATCH、ACCEPT_TIMING_DIFFERENCE、CLASSIFY */
    private String resolutionType;

    /** 归类码；仅 CLASSIFY 非空 */
    private String classificationCode;

    /** 归类金额（带符号），按币种精度补齐；仅 CLASSIFY 非空 */
    private String amount;

    /** 重新匹配目标类型；仅 RE_MATCH 非空 */
    private String rematchTargetType;

    /** 重新匹配目标主键；仅 RE_MATCH 非空 */
    private String rematchTargetId;

    /** 银行层重新匹配的分配金额；仅银行层 RE_MATCH 非空 */
    private String rematchAmount;

    /** 处置说明 */
    private String note;

    /**
     * 处置内容摘要
     * <li>复核时须原样确认。</li>
     */
    private String contentHash;

    /** 提交人 Account ID */
    private String makerId;

    /** 复核人 Account ID；未复核为空 */
    private String checkerId;

    /** 复核决定：APPROVED、REJECTED；未复核为空 */
    private String checkerDecision;

    /** 复核的 UTC 时间；未复核为空 */
    private Instant decisionTime;

    /** 状态：PENDING_CHECK、REJECTED、APPLIED */
    private String status;

    /** 提交的 UTC 时间 */
    private Instant createTime;
}
