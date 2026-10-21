package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.RematchTargetType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_recon_resolution（异常处置）
 * <li>异常处置：Maker 提交、Checker 复核，批准即应用；归类处置按来源对象经 billing 入账。</li>
 */
@Getter
@Setter
public class ReconResolution {
    /**
     * 异常处置ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 对账异常ID
     * <li>所属对账异常。</li>
     */
    private Long exceptionId;

    /**
     * 处置序号
     * <li>同一异常内从 1 起递增的处置序号。</li>
     */
    private Integer resolutionNo;

    /**
     * 处置方式
     * <li>处置方式。允许值：RE_MATCH、ACCEPT_TIMING_DIFFERENCE、CLASSIFY。</li>
     */
    private ResolutionType resolutionType;

    /**
     * 归类码
     * <li>归类码，仅 CLASSIFY 非空，可用范围由异常来源对象决定。允许值：CHANNEL_FEE_OTHER、CHANNEL_RESERVE_HOLD、CHANNEL_RESERVE_RELEASE、BANK_FEE、BANK_INTEREST、INTERNAL_TRANSFER、UNALLOCATED_RECEIPT、PAYOUT_RETURN_PENDING、RECON_DIFFERENCE。</li>
     */
    private ReconClassification classificationCode;

    /**
     * 金额
     * <li>归类金额（带符号），仅 CLASSIFY 非空且等于异常差异金额。</li>
     */
    private BigDecimal amount;

    /**
     * 重新匹配目标类型
     * <li>重新匹配的目标类型，仅 RE_MATCH 非空。允许值：PLATFORM_TXN、EXPECTED_BANK_MOVEMENT。</li>
     */
    private RematchTargetType rematchTargetType;

    /**
     * 重新匹配目标ID
     * <li>重新匹配的目标 ID，仅 RE_MATCH 非空。</li>
     */
    private Long rematchTargetId;

    /**
     * 重新匹配金额
     * <li>银行层重新匹配的分配金额，仅银行层 RE_MATCH 非空。</li>
     */
    private BigDecimal rematchAmount;

    /**
     * 处置说明
     * <li>提交人填写的处置说明。</li>
     */
    private String note;

    /**
     * 内容Hash
     * <li>异常 ID、处置类型与全部输入的 SHA-256；提交后不变。</li>
     */
    private String contentHash;

    /**
     * 提交人ID
     * <li>提交人 Platform 账户 ID。</li>
     */
    private Long makerId;

    /**
     * 复核人ID
     * <li>复核人 Platform 账户 ID，必须与提交人不同。</li>
     */
    private Long checkerId;

    /**
     * 复核决定
     * <li>复核决定。允许值：APPROVED、REJECTED。</li>
     */
    private CheckerDecision checkerDecision;

    /**
     * 复核确认内容Hash
     * <li>复核人确认的内容 Hash，必须等于 content_hash。</li>
     */
    private String approvedContentHash;

    /**
     * 复核时间
     * <li>复核的 UTC 时间。</li>
     */
    private Instant decisionTime;

    /**
     * 状态
     * <li>处置状态；批准即在同一事务应用为 APPLIED。允许值：PENDING_CHECK、REJECTED、APPLIED。</li>
     */
    private ResolutionStatus status;

    /**
     * 乐观锁版本
     * <li>复核更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>提交的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
