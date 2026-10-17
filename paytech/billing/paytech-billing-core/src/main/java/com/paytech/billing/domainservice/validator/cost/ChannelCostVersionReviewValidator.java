package com.paytech.billing.domainservice.validator.cost;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;
import com.paytech.foundation.common.error.BizCheckFailException;

/** 渠道成本版本批准与驳回共用的 Maker-Checker 校验；只读入参，不访问数据库。 */
public final class ChannelCostVersionReviewValidator {

    private ChannelCostVersionReviewValidator() {
    }

    /** 只能复核 PENDING_APPROVAL 版本，复核人不同于编制人，且确认的摘要等于版本内容摘要。 */
    public static void checkReviewable(String planCode, ChannelCostPlanVer ver, long checkerId,
        String approvedContentHash) {
        if (ver.getStatus() != ChannelCostVersionStatus.PENDING_APPROVAL) {
            throw new BizCheckFailException("渠道成本版本不在待复核状态: 方案 " + planCode + ", 版本 " + ver.getCostVer()
                + ", 状态 " + ver.getStatus().getCode());
        }
        if (ver.getMakerId() == checkerId) {
            throw new BizCheckFailException("复核人不能与编制人相同: 方案 " + planCode + ", 版本 " + ver.getCostVer()
                + ", 用户 " + checkerId);
        }
        if (!ver.getContentHash().equals(approvedContentHash)) {
            throw new BizCheckFailException("复核确认的内容摘要与版本不一致: 方案 " + planCode + ", 版本 " + ver.getCostVer());
        }
    }
}
