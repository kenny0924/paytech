package com.paytech.payout.domainservice.service;

import java.time.Duration;

import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domain.BeneficiaryNotificationSource;

/**
 * 收款账户的本地保存：状态迁移、事件追加与通知登记在同一本地事务（B10）
 * <li>调用方 Processor 已在 Owner 锁内完成归属、状态、版本与四眼校验，并在事务外完成 Vault 调用；本服务只做数据库操作。</li>
 * <li>状态、版本由 Repository 条件更新保护，行数不符时 409 并整体回滚；事件序号由唯一索引兜底。</li>
 * <li>核验通过 / 拒绝、停用、关闭在同一事务登记 BENEFICIARY_NOTIFICATION；通知失败只重试任务，不回滚状态。</li>
 */
public interface BeneficiaryAccountService {

    /**
     * 保存新提交的 PENDING_REVIEW 账户与 SUBMIT 事件
     *
     * @param account 已准备主键、编号、Token、摘要与时间的新账户
     * @param sourceKey 来源键
     */
    void submit(BeneficiaryAccount account, String sourceKey);

    /**
     * 记录 Maker 意见与 MAKER_REVIEW 事件
     *
     * @param context 账户与 Maker
     * @param decision 意见
     * @param makerNote 核验意见备注，可为空
     * @param reasonNote 建议拒绝时给 Owner 的说明，可为空
     */
    void recordMakerReview(BeneficiaryCommandContext context, MakerReviewDecision decision, String makerNote,
        String reasonNote);

    /**
     * 记录 Checker 决定（VERIFIED + 冷却 / REJECTED）、CHECKER_DECIDE 事件并登记通知
     *
     * @param context 账户与 Checker
     * @param decision 决定
     * @param reasonNote 拒绝时给 Owner 的说明（随拒绝通知发送），可为空
     * @param cooling 冷却时长
     */
    void recordCheckerDecision(BeneficiaryCommandContext context, CheckerDecision decision, String reasonNote,
        Duration cooling);

    /**
     * 设为默认：先清除同币种原默认（UNSET_DEFAULT 事件），再设本账户（SET_DEFAULT 事件）
     *
     * @param context 账户与操作人
     * @param previousDefault 同 Owner、同币种的原默认账户，可为空
     */
    void setDefault(BeneficiaryCommandContext context, BeneficiaryAccount previousDefault);

    /**
     * 停用（SUSPENDED，清除默认）、SUSPEND 事件并登记通知
     *
     * @param context 账户、运营操作人与停用原因
     */
    void suspend(BeneficiaryCommandContext context);

    /**
     * 关闭（CLOSED，清除默认）、CLOSE 事件并登记通知
     *
     * @param context 账户、操作人与关闭原因
     */
    void close(BeneficiaryCommandContext context);

    /**
     * 恢复为 PENDING_REVIEW 重新核验、RESUME 事件
     *
     * @param context 账户与运营操作人
     */
    void resume(BeneficiaryCommandContext context);

    /**
     * 追加 REVEAL 事件（不改账户）；Vault Reveal 已在事务外成功
     *
     * @param context 账户、查看人与查看原因
     */
    void recordReveal(BeneficiaryCommandContext context);

    /**
     * 通知补偿任务读取账户与触发事件
     *
     * @param beneficiaryAccountId 账户主键
     * @param eventNo 事件序号
     * @return 账户与事件；任一不存在为 null
     */
    BeneficiaryNotificationSource loadNotificationSource(Long beneficiaryAccountId, Integer eventNo);
}
