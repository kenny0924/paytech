package com.paytech.client.contract.api.onboarding.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条已保存的分类。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationItemResponseBody {
    /** 行业代码。 */
    private String industryCode;
    /** 四位 MCC。 */
    private String mccCode;
    /** 是否主要分类。 */
    private boolean primary;
}
