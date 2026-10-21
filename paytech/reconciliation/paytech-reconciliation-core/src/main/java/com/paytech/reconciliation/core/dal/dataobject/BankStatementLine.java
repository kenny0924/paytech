package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;

import lombok.Getter;
import lombok.Setter;

/**
 * t_bank_statement_line（银行流水）
 * <li>银行流水：内容不可变，只推进匹配进度；同一银行账户的银行交易号只入库一次。</li>
 */
@Getter
@Setter
public class BankStatementLine {
    /**
     * 银行流水ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 银行对账单ID
     * <li>所属银行对账单。</li>
     */
    private Long statementId;

    /**
     * 银行账户ID
     * <li>从对账单冗余的银行账户，用于跨对账单去重与匹配。</li>
     */
    private Long cashAccountId;

    /**
     * 行号
     * <li>文件内行号。</li>
     */
    private Integer lineNo;

    /**
     * 银行交易号
     * <li>银行交易号。</li>
     */
    private String bankTransactionRef;

    /**
     * 起息日
     * <li>起息日。</li>
     */
    private LocalDate valueDate;

    /**
     * 方向
     * <li>相对平台账户的方向。允许值：CREDIT、DEBIT。</li>
     */
    private SettlementDirection direction;

    /**
     * 金额
     * <li>流水金额，正数。</li>
     */
    private BigDecimal amount;

    /**
     * 对方名称
     * <li>银行给出的对方名称。</li>
     */
    private String counterpartyName;

    /**
     * 附言
     * <li>附言或参考号，仅用于人工识别。</li>
     */
    private String referenceText;

    /**
     * 已匹配金额
     * <li>已确认匹配按方向带符号的合计（与本流水同向为正），不超过 amount。</li>
     */
    private BigDecimal matchedAmount;

    /**
     * 匹配状态
     * <li>匹配状态；归类结清也记 MATCHED；SUSPENDED 切片 2 起使用。允许值：UNMATCHED、PARTIALLY_MATCHED、MATCHED、EXCEPTION、SUSPENDED。</li>
     */
    private LineMatchStatus matchStatus;

    /**
     * 乐观锁版本
     * <li>匹配进度更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>接收的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
