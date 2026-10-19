package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 科目表列表项。 */
@Getter
@Setter
public class ChartOfAccountsResponseBody {
    /** 科目表主键。 */
    private String chartId;

    /** 科目表代码，全局唯一。 */
    private String chartCode;

    /** 科目表名称。 */
    private String chartName;

    /**
     * 科目表状态
     * <li>DRAFT、ACTIVE 或 INACTIVE；只有 ACTIVE 科目表可用于新建账簿。</li>
     */
    private String status;
}
