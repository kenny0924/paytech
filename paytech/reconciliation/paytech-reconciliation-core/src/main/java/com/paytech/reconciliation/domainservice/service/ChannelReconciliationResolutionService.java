package com.paytech.reconciliation.domainservice.service;

import java.math.BigDecimal;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;

/**
 * 交易层异常处置的应用（technique §2.3「处置应用规则」；B 线提供，C 线在处置批准事务内调用）
 * <li>每个方法都必须在 C 线处置批准的本地事务内调用（传播 REQUIRED 加入该事务），只写账单行 / 平台流水与补偿任务；调用方负责异常结案
 * （{@link ReconExceptionService#resolve}）与处置状态，不在这里改异常或处置。</li>
 * <li>不变量违反（行或平台流水状态不符、方向不一致、金额超过剩余、归类金额与剩余不符）抛 {@code BizCheckFailException}，使批准事务整体回滚；
 * 版本冲突抛 409。</li>
 * <li>应用后调用方须再调用 {@link #scheduleStatementEvaluation} 让匹配任务在提交后重算门禁（并入带交易号的 FEE 行、开成本偏差、对平）。</li>
 */
public interface ChannelReconciliationResolutionService {

    /**
     * RE_MATCH（交易层）：把账单行匹配到指定平台流水
     * <li>行须为交易行、状态 UNMATCHED 或 EXCEPTION、所属账单 MATCHING；平台流水须同清算账户、方向与行类型一致（PAYMENT → 应收，
     * REFUND / CHARGEBACK → 应付）、状态 OPEN 或 PARTIALLY_MATCHED，行金额不超过其剩余金额。</li>
     * <li>行写平台流水、MANUAL、匹配时间、实际费用（行上费用 + 本账单同交易号 FEE 行）、处置 ID，转 MATCHED；平台流水累加已匹配金额与
     * 实际费用，满额转 MATCHED。</li>
     *
     * @param lineId 账单行主键
     * @param platformTxnId 目标平台流水主键
     * @param resolutionId 正在批准的处置主键
     * @return 更新后的账单行
     */
    ChannelStatementLine applyLineRematch(Long lineId, Long platformTxnId, Long resolutionId);

    /**
     * CLASSIFY（账单行）：行写处置 ID 并转 MATCHED，不写平台流水；归类码与带符号金额由处置记录携带，随该账单的 ChannelBatchReconciled 带出
     * <li>行须为 UNMATCHED 或 EXCEPTION，所属账单 MATCHING；金额与异常差异相等由调用方校验。</li>
     *
     * @param lineId 账单行主键
     * @param resolutionId 正在批准的处置主键
     * @return 更新后的账单行
     */
    ChannelStatementLine applyLineClassification(Long lineId, Long resolutionId);

    /**
     * CLASSIFY（平台流水，如 PLATFORM_ITEM_UNSETTLED）：剩余未结清金额视为结清，平台流水转 SETTLED
     * <li>平台流水不能已是 SETTLED；归类金额的绝对值必须等于 expected_amount − settled_amount。ReconAdjustmentApproved 由调用方登记。</li>
     *
     * @param platformTxnId 平台流水主键
     * @param signedAmount 处置的带符号归类金额
     * @return 更新后的平台流水
     */
    PlatformTxn applyPlatformTxnClassification(Long platformTxnId, BigDecimal signedAmount);

    /**
     * ACCEPT_TIMING_DIFFERENCE（DUPLICATE_SETTLEMENT，确认不是重复）：行 EXCEPTION → UNMATCHED，交还匹配任务
     * <li>调用方须先把该 DUPLICATE_SETTLEMENT 异常结案为 RESOLVED；匹配任务看到该行最近一条重复结算异常已 RESOLVED，就跳过跨账单查重，按
     * 规则 1–5 继续匹配。</li>
     *
     * @param lineId 账单行主键
     * @return 更新后的账单行
     */
    ChannelStatementLine releaseLineForRematching(Long lineId);

    /**
     * 处置应用后登记 CHANNEL_STATEMENT_MATCH（来源 RESOLUTION-&lt;resolutionId&gt;），提交后重跑匹配与对平门禁
     * <li>以处置 ID 区分来源，避免在匹配任务执行中登记被当作重复而丢失唤醒。账单已不在 MATCHING 时任务直接结束。</li>
     *
     * @param statementId 渠道账单主键
     * @param resolutionId 已应用的处置主键
     */
    void scheduleStatementEvaluation(Long statementId, Long resolutionId);
}
