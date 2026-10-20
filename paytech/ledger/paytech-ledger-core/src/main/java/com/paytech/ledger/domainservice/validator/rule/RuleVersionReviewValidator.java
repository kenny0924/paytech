package com.paytech.ledger.domainservice.validator.rule;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;

/** 规则版本批准与驳回共用的 Maker-Checker 校验；只读入参，不访问数据库。 */
public final class RuleVersionReviewValidator {

    private RuleVersionReviewValidator() {
    }

    /** 只能复核 PENDING_APPROVAL 版本，且复核人不同于编制人。 */
    public static void checkReviewable(String bundleCode, AccountingRuleBundleVer ver, long checkerId) {
        if (ver.getStatus() != RuleVersionStatus.PENDING_APPROVAL) {
            throw new FailException(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);
        }
        if (ver.getMakerId() == checkerId) {
            throw new FailException(LedgerErrorCode.SELF_REVIEW_FORBIDDEN);
        }
    }
}
