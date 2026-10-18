package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 单个账簿的身份与科目表。 */
@Getter
@Setter
public class LedgerBookDetailResponseBody {
    /** 账簿主键。 */
    private String ledgerBookId;

    /** 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    private String entityType;

    /** 主体标识：OE 为 entityNo，Finance Entity 为本地主键。 */
    private String entityId;

    /** 账簿币种。 */
    private String currency;

    /** 采用的科目表主键。 */
    private String chartId;

    /** 采用的科目表代码。 */
    private String chartCode;

    /** 账簿状态：OPEN、SUSPENDED 或 CLOSED。 */
    private String bookStatus;
}
