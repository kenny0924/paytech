package com.paytech.ledger.domainservice.validator.rule;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;

/** 规则版本批准与驳回共用的 Maker-Checker 校验；只读入参，不访问数据库。 */
public final class RuleVersionReviewValidator {

    private RuleVersionReviewValidator() {
    }

    /** 只能复核 PENDING_APPROVAL 版本，且复核人不同于编制人。 */
    public static void checkReviewable(String bundleCode, AccountingRuleBundleVer ver, long checkerId) {
        if (ver.getStatus() != RuleVersionStatus.PENDING_APPROVAL) {
            throw new BizCheckFailException("规则版本不在待复核状态: 规则集 " + bundleCode + ", 版本 " + ver.getBundleVer()
                + ", 状态 " + ver.getStatus().getCode());
        }
        if (ver.getMakerId() == checkerId) {
            throw new BizCheckFailException("复核人不能与编制人相同: 规则集 " + bundleCode + ", 版本 " + ver.getBundleVer()
                + ", 用户 " + checkerId);
        }
    }
}
