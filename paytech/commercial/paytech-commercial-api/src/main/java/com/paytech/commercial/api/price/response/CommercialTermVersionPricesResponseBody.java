package com.paytech.commercial.api.price.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 固定版本下某 OE 的价格全集（stage3 新增 5）；最多 1000 条，不截断。 */
@Getter
@Setter
public class CommercialTermVersionPricesResponseBody {
    /** 版本 ID */
    private String termVersionId;

    /** 条款类型 */
    private String termType;

    /** 准确 OE */
    private String entityNo;

    /** 价格，按币种、优先级排序 */
    private List<CommercialPriceResponseBody> prices;
}
