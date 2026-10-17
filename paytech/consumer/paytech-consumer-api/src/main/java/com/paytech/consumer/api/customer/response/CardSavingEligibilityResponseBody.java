package com.paytech.consumer.api.customer.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 存卡资格。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CardSavingEligibilityResponseBody {
    /** 是否可保存卡或使用已存卡。 */
    private boolean eligible;

    /** 不可时的原因，取值见 {@link com.paytech.consumer.api.enums.CardSavingIneligibleReason}；可用时为空。 */
    private String reason;

    /** Customer 存在时的公开编号；不存在时为空。 */
    private String customerNo;
}
