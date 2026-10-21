package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_channel_statement（渠道账单）
 * <li>渠道账单（外部账单）：一个渠道结算批次的一个导入版本；头内容导入后不可变，只推进状态与到账进度。</li>
 */
@Getter
@Setter
public class ChannelStatement {
    /**
     * 渠道账单ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 清算账户ID
     * <li>所属清算账户。</li>
     */
    private Long clearingAccountId;

    /**
     * 渠道批次号
     * <li>渠道结算批次号。</li>
     */
    private String channelBatchRef;

    /**
     * 导入版本
     * <li>同一批次号的导入版本，从 1 起；同一批次号只允许一个未作废版本。</li>
     */
    private Integer revision;

    /**
     * 结算日
     * <li>渠道结算日。</li>
     */
    private LocalDate settlementDate;

    /**
     * 币种
     * <li>账单币种，必须等于清算账户币种。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 总额
     * <li>账单头总额（带符号：贷记减借记，不含费用行）。</li>
     */
    private BigDecimal grossAmount;

    /**
     * 费用金额
     * <li>账单头费用合计，非负。</li>
     */
    private BigDecimal feeAmount;

    /**
     * 净额
     * <li>净额绝对值，方向见 cash_direction。</li>
     */
    private BigDecimal netAmount;

    /**
     * 净额方向
     * <li>净额相对平台的方向；NONE 表示净额为 0。允许值：CREDIT、DEBIT、NONE。</li>
     */
    private CashDirection cashDirection;

    /**
     * 声明行数
     * <li>开始导入时声明的明细行数。</li>
     */
    private Integer declaredLineCount;

    /**
     * 已接收行数
     * <li>已接收的明细行数。</li>
     */
    private Integer importedLineCount;

    /**
     * 文件名
     * <li>运营上传的原文件名，仅用于展示。</li>
     */
    private String fileName;

    /**
     * 文件Hash
     * <li>浏览器对原文件计算的 SHA-256；不保存原文件。</li>
     */
    private String fileContentHash;

    /**
     * 拒绝原因
     * <li>整单被拒的汇总原因；逐行原因只在完成导入的响应中返回。</li>
     */
    private String rejectReason;

    /**
     * 状态
     * <li>账单状态。允许值：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED。</li>
     */
    private ChannelStatementStatus status;

    /**
     * 到账确认金额
     * <li>净额中已经银行确认或归类结清的金额。</li>
     */
    private BigDecimal cashConfirmedAmount;

    /**
     * 对平时间
     * <li>对平的 UTC 时间；未对平为空。</li>
     */
    private Instant reconciledTime;

    /**
     * 提交人ID
     * <li>上传人（Platform 账户 ID）。</li>
     */
    private Long makerId;

    /**
     * 乐观锁版本
     * <li>状态与进度更新的乐观锁版本。</li>
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
