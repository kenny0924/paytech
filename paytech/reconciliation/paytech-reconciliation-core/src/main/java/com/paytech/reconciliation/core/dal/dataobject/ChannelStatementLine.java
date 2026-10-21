package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.SettlementDirection;

import lombok.Getter;
import lombok.Setter;

/**
 * t_channel_statement_line（渠道账单行）
 * <li>渠道账单行：文件内容不可变；匹配到的平台流水、实际渠道费和归类处置记在行上。</li>
 */
@Getter
@Setter
public class ChannelStatementLine {
    /**
     * 渠道账单行ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 渠道账单ID
     * <li>所属渠道账单。</li>
     */
    private Long statementId;

    /**
     * 清算账户ID
     * <li>从账单冗余的清算账户，用于跨账单查重与匹配。</li>
     */
    private Long clearingAccountId;

    /**
     * 行号
     * <li>文件内行号，从 1 起连续。</li>
     */
    private Integer lineNo;

    /**
     * 行类型
     * <li>行类型。允许值：PAYMENT、REFUND、CHARGEBACK、FEE、ADJUSTMENT。</li>
     */
    private ChannelLineType lineType;

    /**
     * 方向
     * <li>相对平台的资金方向。允许值：CREDIT、DEBIT。</li>
     */
    private SettlementDirection direction;

    /**
     * 金额
     * <li>行金额，非负。</li>
     */
    private BigDecimal amount;

    /**
     * 费用金额
     * <li>行上直接扣的渠道费，非负。</li>
     */
    private BigDecimal feeAmount;

    /**
     * 外部交易号类型
     * <li>外部交易号类型；ADJUSTMENT 行与不带交易号的 FEE 行为空。取值来源：Payment 渠道交易引用类型目录；非固定枚举。</li>
     */
    private String referenceType;

    /**
     * 外部交易号
     * <li>渠道外部交易号；为空规则同 reference_type。</li>
     */
    private String externalTransactionId;

    /**
     * 交易日期
     * <li>渠道记录的交易日期。</li>
     */
    private LocalDate transactionDate;

    /**
     * 渠道原始属性
     * <li>标准列以外的渠道特有列，原样保存为 JSON Object，最大 4 KiB；不参与匹配。</li>
     */
    private String rawAttributes;

    /**
     * 平台流水ID
     * <li>匹配到的平台流水；一行最多对一笔。</li>
     */
    private Long platformTxnId;

    /**
     * 匹配方式
     * <li>匹配方式；未匹配为空。允许值：AUTO、MANUAL。</li>
     */
    private MatchMethod matchMethod;

    /**
     * 匹配时间
     * <li>匹配的 UTC 时间。</li>
     */
    private Instant matchedTime;

    /**
     * 实际渠道费
     * <li>确认的实际渠道费（本行 fee_amount 加同交易号 FEE 行）；FEE 行本身记在交易行上，FEE 行此列为空。</li>
     */
    private BigDecimal actualFee;

    /**
     * 处置ID
     * <li>已归类调整行指向的已应用处置；随账单对平事件带出。</li>
     */
    private Long resolutionId;

    /**
     * 匹配状态
     * <li>匹配状态；归类或并入交易行的 FEE 行也记 MATCHED。允许值：UNMATCHED、MATCHED、EXCEPTION。</li>
     */
    private LineMatchStatus matchStatus;

    /**
     * 乐观锁版本
     * <li>匹配结果更新的乐观锁版本。</li>
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
