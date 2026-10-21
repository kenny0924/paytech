package com.paytech.reconciliation.api.bank.response;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 银行流水：文件内容与匹配进度。 */
@Getter
@Setter
public class BankStatementLineResponseBody {
    /** 银行流水主键 */
    private String bankStatementLineId;

    /** 银行对账单主键 */
    private String bankStatementId;

    /** 银行账户主键 */
    private String cashAccountId;

    /** 文件内行号 */
    private Integer lineNo;

    /** 银行交易号 */
    private String bankTransactionRef;

    /** 起息日 */
    private LocalDate valueDate;

    /** 方向：CREDIT、DEBIT */
    private String direction;

    /** 流水金额，按币种精度补齐 */
    private String amount;

    /** 币种（取自银行账户） */
    private String currency;

    /** 对方名称；可为空 */
    private String counterpartyName;

    /** 附言或参考号；可为空 */
    private String referenceText;

    /** 已确认匹配按方向带符号的合计（与本流水同向为正），按币种精度补齐 */
    private String matchedAmount;

    /** 匹配状态：UNMATCHED、PARTIALLY_MATCHED、MATCHED、EXCEPTION、SUSPENDED */
    private String matchStatus;

    /** 乐观锁版本 */
    private String version;
}
