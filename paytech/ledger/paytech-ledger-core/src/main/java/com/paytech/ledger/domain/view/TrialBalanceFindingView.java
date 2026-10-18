package com.paytech.ledger.domain.view;

import java.math.BigDecimal;

import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.core.enums.TrialExceptionType;
import com.paytech.ledger.core.enums.TrialReferenceType;

import lombok.Getter;
import lombok.Setter;

/** 试算异常发现事实与当前处置状态。 */
@Getter
@Setter
public class TrialBalanceFindingView {
    /** 异常分类。 */
    private TrialExceptionType exceptionType;

    /** 引用对象类型。 */
    private TrialReferenceType referenceType;

    /** 引用对象主键。 */
    private Long referenceId;

    /** 绝对差异金额。 */
    private BigDecimal differenceAmount;

    /** 处置状态。 */
    private TrialExceptionStatus status;
}
