package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 账簿列表项。 */
@Getter
@Setter
public class LedgerBookResponseBody {
    /** 账簿主键。 */
    private String ledgerBookId;

    /**
     * 主体类型
     * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
     */
    private String entityType;

    /**
     * 主体标识
     * <li>OE 为 entityNo，Finance Entity 为本地主键。</li>
     */
    private String entityId;

    /** 账簿币种。 */
    private String currency;

    /** 采用的科目表代码。 */
    private String chartCode;

    /**
     * 账簿状态
     * <li>OPEN、SUSPENDED 或 CLOSED。</li>
     */
    private String bookStatus;
}
