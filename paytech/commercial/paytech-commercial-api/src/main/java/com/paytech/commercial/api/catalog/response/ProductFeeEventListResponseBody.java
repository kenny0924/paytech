package com.paytech.commercial.api.catalog.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 计费事件列表（目录规模有限，不分页）。 */
@Getter
@Setter
public class ProductFeeEventListResponseBody {
    /** 按计费事件 ID 升序 */
    private List<ProductFeeEventSummary> feeEvents;
}
