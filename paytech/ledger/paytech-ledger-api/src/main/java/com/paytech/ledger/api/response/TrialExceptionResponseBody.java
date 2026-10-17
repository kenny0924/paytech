package com.paytech.ledger.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 试算异常及其解决状态。 */
@Getter
@Setter
public class TrialExceptionResponseBody {
    /** 异常主键。 */
    private String exceptionId;

    /** 发现该异常的试算主键。 */
    private String runId;

    /** 异常类型，例如 GENERAL_LEDGER_MISMATCH。 */
    private String exceptionType;

    /** 引用对象类型：ACCOUNT、CHART_ACCOUNT、CASH_ACCOUNT 或 RECON_EXCEPTION。 */
    private String referenceType;

    /** 引用对象主键。 */
    private String referenceId;

    /** 差异金额，主货币单位。 */
    private String differenceAmount;

    /** 处置状态：OPEN、UNDER_REVIEW、RESOLVED 或 WAIVED。 */
    private String status;

    /**
     * 解决方式，例如 PROJECTION_REBUILD
     * <li>OPEN 时为空。</li>
     */
    private String resolutionType;

    /**
     * 解决证据标识
     * <li>投影重建为账户主键；RESOLVED 前为空。</li>
     */
    private String resolutionRefId;

    /**
     * 解决内容摘要，64 位小写十六进制
     * <li>RESOLVED 前为空。</li>
     */
    private String resolutionContentHash;

    /**
     * 编制人
     * <li>OPEN 时为空。</li>
     */
    private String resolutionMakerId;

    /**
     * 复核人
     * <li>RESOLVED 前为空。</li>
     */
    private String resolutionCheckerId;

    /**
     * 解决完成时间（UTC）
     * <li>RESOLVED 前为空。</li>
     */
    private Instant resolutionTime;
}
