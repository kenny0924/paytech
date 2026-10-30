package com.paytech.payout.domainservice.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;

/** t_beneficiary_account（收款账户）仓储：只做本表读写与行数检查，不开事务；条件更新成功后同步传入对象。 */
public interface BeneficiaryAccountRepository {
    /**
     * 插入新账户；同 Owner 同账号未终结账户的唯一性由 uk_beneficiary_owner_token 兜底。
     *
     * @param record 已准备主键、Token、摘要与时间的新记录
     */
    void insert(BeneficiaryAccount record);

    /**
     * 按主键读取。
     *
     * @param id 主键
     * @return 记录；不存在为 null
     */
    BeneficiaryAccount findById(Long id);

    /**
     * 按 Owner 读取，可选状态与币种过滤。
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @param status 可选状态
     * @param currency 可选币种
     * @return 按币种、创建时间、主键升序
     */
    List<BeneficiaryAccount> listByOwner(BeneficiaryOwnerType ownerType, Long ownerId, BeneficiaryAccountStatus status,
        String currency);

    /**
     * 同 Owner 同账号的未终结账户（B6 查重）。
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @param bankAccountToken 账号 STABLE Token
     * @return 记录；没有为 null
     */
    BeneficiaryAccount findActiveByOwnerToken(BeneficiaryOwnerType ownerType, Long ownerId, String bankAccountToken);

    /**
     * 同 Owner 当前最大的账户编号。
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @return 编号；没有账户为 null
     */
    String findMaxAccountCode(BeneficiaryOwnerType ownerType, Long ownerId);

    /**
     * 同 Owner、同币种的当前默认账户。
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @param currency 币种
     * @return 记录；没有为 null
     */
    BeneficiaryAccount findDefault(BeneficiaryOwnerType ownerType, Long ownerId, String currency);

    /**
     * 其他 Owner 以同一账号持有的未终结账户（运营视图共用提示），按创建时间升序。
     *
     * @param account 被查看的账户
     * @param limit 最多条数
     * @return 其他 Owner 的账户；没有时为空列表
     */
    List<BeneficiaryAccount> listSharedAccounts(BeneficiaryAccount account, int limit);

    /**
     * 核验队列分页。
     *
     * @param stage 阶段
     * @param ownerType 可选 Owner 类型
     * @param ownerId 可选 Owner 标识
     * @param decidedSince DONE 阶段的最早决定时间；其他阶段不使用
     * @param offset 偏移量
     * @param limit 每页条数
     * @return 当前页
     */
    List<BeneficiaryAccount> findReviewPage(BeneficiaryReviewStage stage, BeneficiaryOwnerType ownerType, Long ownerId,
        Instant decidedSince, long offset, int limit);

    /**
     * 与 findReviewPage 相同条件的总数。
     *
     * @param stage 阶段
     * @param ownerType 可选 Owner 类型
     * @param ownerId 可选 Owner 标识
     * @param decidedSince DONE 阶段的最早决定时间
     * @return 总数
     */
    long countReviewPage(BeneficiaryReviewStage stage, BeneficiaryOwnerType ownerType, Long ownerId, Instant decidedSince);

    /**
     * 记录 Maker 意见；状态仍为 PENDING_REVIEW。
     *
     * @param record 已读取的账户
     * @param makerId Maker 账号
     * @param decision 意见
     * @param reasonCode 拒绝原因，可为空
     * @param makerNote 核验意见备注，可为空
     * @param reasonNote 建议拒绝时给 Owner 的说明，可为空
     */
    void recordMakerReview(BeneficiaryAccount record, Long makerId, MakerReviewDecision decision, String reasonCode,
        String makerNote, String reasonNote);

    /**
     * 记录 Checker 决定：批准 → VERIFIED 并设冷却结束时间 = 决定时间 + cooling；拒绝 → REJECTED。
     *
     * @param record 已读取的账户
     * @param checkerId Checker 账号
     * @param decision 决定
     * @param reasonCode 拒绝原因，可为空
     * @param reasonNote 拒绝时给 Owner 的说明，可为空
     * @param cooling 冷却时长
     */
    void recordCheckerDecision(BeneficiaryAccount record, Long checkerId, CheckerDecision decision, String reasonCode,
        String reasonNote, Duration cooling);

    /**
     * 设置或清除默认标记。
     *
     * @param record 已读取的账户
     * @param isDefault 目标标记
     */
    void updateDefault(BeneficiaryAccount record, boolean isDefault);

    /**
     * 停用或关闭：迁移状态、记原因并清除默认。
     *
     * @param record 已读取的账户
     * @param status 目标状态
     * @param reasonCode 原因
     */
    void updateStatusWithReason(BeneficiaryAccount record, BeneficiaryAccountStatus status, String reasonCode);

    /**
     * 恢复：SUSPENDED → PENDING_REVIEW，清空核验结果、冷却与原因。
     *
     * @param record 已读取的 SUSPENDED 账户
     */
    void resume(BeneficiaryAccount record);
}
