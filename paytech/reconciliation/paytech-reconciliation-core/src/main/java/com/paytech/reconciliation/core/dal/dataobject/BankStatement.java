package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.reconciliation.api.enums.BankStatementStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_bank_statement（银行对账单）
 * <li>银行对账单头：期间与银行报告的期初期末余额，导入时校验期初加流水等于期末。</li>
 */
@Getter
@Setter
public class BankStatement {
    /**
     * 银行对账单ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 银行账户ID
     * <li>所属银行账户。</li>
     */
    private Long cashAccountId;

    /**
     * 对账单编号
     * <li>银行对账单编号。</li>
     */
    private String statementRef;

    /**
     * 期间开始日
     * <li>对账单期间开始日（含）。</li>
     */
    private LocalDate periodStart;

    /**
     * 期间结束日
     * <li>对账单期间结束日（含）。</li>
     */
    private LocalDate periodEnd;

    /**
     * 币种
     * <li>币种，必须等于银行账户币种。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 期初余额
     * <li>银行报告的期初余额（带符号）。</li>
     */
    private BigDecimal openingBalance;

    /**
     * 期末余额
     * <li>银行报告的期末余额（带符号），须等于期初加流水。</li>
     */
    private BigDecimal closingBalance;

    /**
     * 声明行数
     * <li>开始导入时声明的流水行数。</li>
     */
    private Integer declaredLineCount;

    /**
     * 已接收行数
     * <li>已接收的流水行数。</li>
     */
    private Integer importedLineCount;

    /**
     * 重复行数
     * <li>因银行交易号已存在而跳过的行数。</li>
     */
    private Integer duplicateLineCount;

    /**
     * 文件名
     * <li>原文件名，仅用于展示。</li>
     */
    private String fileName;

    /**
     * 文件Hash
     * <li>浏览器对原文件计算的 SHA-256；不保存原文件。</li>
     */
    private String fileContentHash;

    /**
     * 拒绝原因
     * <li>整单被拒的汇总原因。</li>
     */
    private String rejectReason;

    /**
     * 状态
     * <li>导入状态。允许值：RECEIVING、IMPORTED、REJECTED。</li>
     */
    private BankStatementStatus status;

    /**
     * 提交人ID
     * <li>上传人（Platform 账户 ID）。</li>
     */
    private Long makerId;

    /**
     * 乐观锁版本
     * <li>导入进度更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>开始导入的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
