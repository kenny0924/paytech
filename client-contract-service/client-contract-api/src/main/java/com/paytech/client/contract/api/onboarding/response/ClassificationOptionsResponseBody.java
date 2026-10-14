package com.paytech.client.contract.api.onboarding.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 运营分类可选目录。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationOptionsResponseBody {
    /** 通用行业。 */
    private List<CatalogOptionResponseBody> industries;
}
