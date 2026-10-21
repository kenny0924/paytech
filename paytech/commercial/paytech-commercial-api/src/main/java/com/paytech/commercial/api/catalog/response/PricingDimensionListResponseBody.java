package com.paytech.commercial.api.catalog.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 维度清单（代码登记，不分页）。 */
@Getter
@Setter
public class PricingDimensionListResponseBody {
    /** 按枚举登记顺序 */
    private List<PricingDimensionSummary> dimensions;
}
