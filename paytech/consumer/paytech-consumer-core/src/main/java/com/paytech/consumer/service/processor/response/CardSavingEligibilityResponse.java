package com.paytech.consumer.service.processor.response;

import com.paytech.consumer.api.enums.CardSavingIneligibleReason;

/**
 * 存卡资格结论
 *
 * @param reason 不可存卡的原因；可用时为空
 * @param customerNo Customer 属于请求 Client 时的公开编号；不存在或跨 Client 时为空
 */
public record CardSavingEligibilityResponse(CardSavingIneligibleReason reason, String customerNo) {
    /** 可保存卡或使用已存卡。 */
    public static CardSavingEligibilityResponse eligible(String customerNo) {
        return new CardSavingEligibilityResponse(null, customerNo);
    }

    /** 没有不可存卡的原因即为可用。 */
    public boolean isEligible() {
        return reason == null;
    }
}
