package com.paytech.commercial.api.catalog.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 可开通项列表（目录规模有限，不分页）。 */
@Getter
@Setter
public class ProductCapabilityListResponseBody {
    /** 按可开通项 ID 升序 */
    private List<ProductCapabilitySummary> capabilities;
}
