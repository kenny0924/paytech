package com.paytech.payment.api.card.response;

import lombok.Getter;
import lombok.Setter;

/** 按 Customer 失效已存卡响应 */
@Getter
@Setter
public class InvalidateStoredCardsResponseBody {
    /** 本次失效条数 */
    private int invalidatedCount;
}
