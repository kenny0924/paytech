package com.paytech.payout.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;

/** t_beneficiary_account：收款账户的 MyBatis 映射；SQL 位于 mapper/payout/BeneficiaryAccountMapper.xml。 */
@Mapper
public interface BeneficiaryAccountMapper {
    /** 插入一行；主键由应用分配，生成列 active_key 由数据库计算。 */
    int insert(BeneficiaryAccount record);

    /** 按主键读取，不存在返回 null。 */
    BeneficiaryAccount selectById(@Param("id") Long id);

    /** 按 Owner 与可选状态、币种读取，按币种、创建时间、主键升序。 */
    List<BeneficiaryAccount> selectByOwner(@Param("ownerType") BeneficiaryOwnerType ownerType,
        @Param("ownerId") Long ownerId, @Param("status") BeneficiaryAccountStatus status,
        @Param("currency") String currency);

    /** 同 Owner 同账号 Token 的未终结账户（非 REJECTED / CLOSED），至多一行。 */
    BeneficiaryAccount selectActiveByOwnerToken(@Param("ownerType") BeneficiaryOwnerType ownerType,
        @Param("ownerId") Long ownerId, @Param("bankAccountToken") String bankAccountToken);

    /** 同 Owner 当前最大的账户编号；没有账户返回 null。 */
    String selectMaxAccountCode(@Param("ownerType") BeneficiaryOwnerType ownerType, @Param("ownerId") Long ownerId);

    /** 同 Owner、同币种的当前默认账户，至多一行。 */
    BeneficiaryAccount selectDefault(@Param("ownerType") BeneficiaryOwnerType ownerType, @Param("ownerId") Long ownerId,
        @Param("currency") String currency);

    /** 其他 Owner 使用同一账号 Token 的未终结账户，按创建时间升序，最多 limit 条。 */
    List<BeneficiaryAccount> selectOtherOwnerAccounts(@Param("bankAccountToken") String bankAccountToken,
        @Param("ownerType") BeneficiaryOwnerType ownerType, @Param("ownerId") Long ownerId, @Param("limit") int limit);

    /** 核验队列分页。 */
    List<BeneficiaryAccount> selectReviewPage(@Param("stage") BeneficiaryReviewStage stage,
        @Param("ownerType") BeneficiaryOwnerType ownerType, @Param("ownerId") Long ownerId,
        @Param("decidedSince") Instant decidedSince, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectReviewPage 相同条件的总数。 */
    long countReviewPage(@Param("stage") BeneficiaryReviewStage stage, @Param("ownerType") BeneficiaryOwnerType ownerType,
        @Param("ownerId") Long ownerId, @Param("decidedSince") Instant decidedSince);

    /** 记录 Maker 意见：仅 PENDING_REVIEW 且尚无意见、版本一致时生效。 */
    int updateMakerReview(@Param("record") BeneficiaryAccount record, @Param("makerId") Long makerId,
        @Param("decision") MakerReviewDecision decision, @Param("reasonCode") String reasonCode,
        @Param("makerNote") String makerNote, @Param("reasonNote") String reasonNote, @Param("now") Instant now);

    /** 记录 Checker 决定并迁移状态：仅 PENDING_REVIEW、已有 Maker 意见、版本一致时生效。 */
    int updateCheckerDecision(@Param("record") BeneficiaryAccount record, @Param("status") BeneficiaryAccountStatus status,
        @Param("checkerId") Long checkerId, @Param("decision") CheckerDecision decision,
        @Param("reasonCode") String reasonCode, @Param("reasonNote") String reasonNote,
        @Param("coolingUntil") Instant coolingUntil, @Param("now") Instant now);

    /** 设置或清除默认标记：按版本与原状态条件更新。 */
    int updateDefault(@Param("record") BeneficiaryAccount record, @Param("isDefault") boolean isDefault,
        @Param("now") Instant now);

    /** 停用或关闭：迁移状态、记原因并清除默认，按版本与原状态条件更新。 */
    int updateStatusWithReason(@Param("record") BeneficiaryAccount record,
        @Param("status") BeneficiaryAccountStatus status, @Param("reasonCode") String reasonCode,
        @Param("now") Instant now);

    /** 恢复：SUSPENDED → PENDING_REVIEW，清空核验结果、冷却与原因，按版本条件更新。 */
    int updateResume(@Param("record") BeneficiaryAccount record, @Param("now") Instant now);
}
