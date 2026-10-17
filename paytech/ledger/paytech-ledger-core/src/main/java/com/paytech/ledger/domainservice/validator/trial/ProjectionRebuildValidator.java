package com.paytech.ledger.domainservice.validator.trial;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.core.enums.TrialExceptionType;
import com.paytech.ledger.core.enums.TrialReferenceType;

/** 投影重建发起与批准共用的纯校验；只读入参，不访问数据库。 */
public final class ProjectionRebuildValidator {

    private ProjectionRebuildValidator() {
    }

    /** 异常必须存在，且为可投影重建的总账账户异常，并处于给定状态。 */
    public static void checkRebuildable(TrialBalanceException exception, long exceptionId,
        TrialExceptionStatus expectedStatus) {
        if (exception == null) {
            throw new BizCheckFailException("试算异常不存在: " + exceptionId);
        }
        if (exception.getExceptionType() != TrialExceptionType.GENERAL_LEDGER_MISMATCH
            || exception.getReferenceType() != TrialReferenceType.ACCOUNT) {
            throw new BizCheckFailException("只有 GENERAL_LEDGER_MISMATCH/ACCOUNT 异常可以投影重建: " + exceptionId);
        }
        if (exception.getStatus() != expectedStatus) {
            throw new BizCheckFailException("试算异常状态不允许该操作: " + exceptionId + ", 状态 "
                + exception.getStatus().getCode());
        }
    }

    /** 复核人必须不同于发起重建的编制人。 */
    public static void checkChecker(TrialBalanceException exception, long checkerId) {
        if (exception.getResolutionMakerId() == checkerId) {
            throw new BizCheckFailException("复核人不能与编制人相同: 异常 " + exception.getId() + ", 用户 " + checkerId);
        }
    }
}
