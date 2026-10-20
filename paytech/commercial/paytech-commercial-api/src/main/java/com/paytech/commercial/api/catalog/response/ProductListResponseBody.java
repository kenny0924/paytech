package com.paytech.commercial.api.catalog.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 产品列表（目录规模有限，不分页）。 */
@Getter
@Setter
public class ProductListResponseBody {
    /** 按产品 ID 升序 */
    private List<ProductSummary> products;
}
