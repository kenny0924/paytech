package com.paytech.ledger.domain.view;

import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;

import lombok.Getter;
import lombok.Setter;

/** 账簿与其采用科目表代码的组合读取结果。 */
@Getter
@Setter
public class LedgerBookView {
    /** 账簿主键。 */
    private Long ledgerBookId;

    /** 主体类型。 */
    private LedgerEntityType entityType;

    /** 主体标识。 */
    private String entityId;

    /** 账簿币种。 */
    private String currency;

    /** 采用的科目表代码。 */
    private String chartCode;

    /** 账簿状态。 */
    private BookStatus bookStatus;
}
