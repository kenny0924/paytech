package com.paytech.client.master.api.onboardingadmin.response;

import lombok.Getter;
import lombok.Setter;

/** CM-10 通用目录项（全部辖区、全部状态）。 */
@Getter
@Setter
public class AdminCatalogItemResponseBody {
    /** 目录代码。 */
    private String catalogCode;

    /** 目录项代码。 */
    private String itemCode;

    /** 辖区；* 为全局。 */
    private String jurisdictionCode;

    /** 展示文案键。 */
    private String labelKey;

    /** 适用 Client 类型，逗号分隔；为空表示都适用。 */
    private String clientTypes;

    /** 展示顺序。 */
    private int displayOrder;

    /** 状态。 */
    private String status;
}
