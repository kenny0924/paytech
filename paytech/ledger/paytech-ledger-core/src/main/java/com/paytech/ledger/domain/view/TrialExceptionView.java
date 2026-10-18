package com.paytech.ledger.domain.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.core.enums.TrialExceptionType;
import com.paytech.ledger.core.enums.TrialReferenceType;

import lombok.Getter;
import lombok.Setter;

/** 试算异常及其解决字段的读取结果。 */
@Getter
@Setter
public class TrialExceptionView {
    /** 异常主键。 */
    private Long exceptionId;

    /** 发现该异常的试算主键。 */
    private Long runId;

    /** 异常类型。 */
    private TrialExceptionType exceptionType;

    /** 引用对象类型。 */
    private TrialReferenceType referenceType;

    /** 引用对象主键。 */
    private Long referenceId;

    /** 差异金额。 */
    private BigDecimal differenceAmount;

    /** 处置状态。 */
    private TrialExceptionStatus status;

    /**
     * 解决方式
     * <li>OPEN 时为空。</li>
     */
    private String resolutionType;

    /**
     * 解决证据标识
     * <li>RESOLVED 前为空；投影重建时为账户主键。</li>
     */
    private Long resolutionRefId;

    /**
     * 解决内容摘要
     * <li>RESOLVED 前为空。</li>
     */
    private String resolutionContentHash;

    /**
     * 发起解决的编制人
     * <li>OPEN 时为空。</li>
     */
    private Long resolutionMakerId;

    /**
     * 批准解决的复核人
     * <li>RESOLVED 前为空。</li>
     */
    private Long resolutionCheckerId;

    /**
     * 解决完成时间
     * <li>RESOLVED 前为空。</li>
     */
    private Instant resolutionTime;

    /** 复制异常当前状态与解决字段，差异金额按账簿币种精度补齐。 */
    public static TrialExceptionView of(TrialBalanceException exception, int minorUnit) {
        TrialExceptionView view = new TrialExceptionView();
        view.setExceptionId(exception.getId());
        view.setRunId(exception.getRunId());
        view.setExceptionType(exception.getExceptionType());
        view.setReferenceType(exception.getReferenceType());
        view.setReferenceId(exception.getReferenceId());
        view.setDifferenceAmount(exception.getDifferenceAmount().setScale(minorUnit, RoundingMode.HALF_UP));
        view.setStatus(exception.getStatus());
        view.setResolutionType(exception.getResolutionType());
        view.setResolutionRefId(exception.getResolutionRefId());
        view.setResolutionContentHash(exception.getResolutionContentHash());
        view.setResolutionMakerId(exception.getResolutionMakerId());
        view.setResolutionCheckerId(exception.getResolutionCheckerId());
        view.setResolutionTime(exception.getResolutionTime());
        return view;
    }
}
