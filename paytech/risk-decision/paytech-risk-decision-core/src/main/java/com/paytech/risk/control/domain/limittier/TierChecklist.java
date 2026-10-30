package com.paytech.risk.control.domain.limittier;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;

/**
 * 上线复核清单（VERIFICATION → OBSERVATION 申请时冻结）
 *
 * @param qualificationStatus 系统项：资格状态
 * @param qualificationValidTo 系统项：资格有效期；可空
 * @param underwritingVersionNo 系统项：有效承保版本号；可空
 * @param agreementId 系统项：CLIENT 合同 ID；可空
 * @param agreementStatus 系统项：合同状态；可空
 * @param enabledCapabilities 系统项：ENABLED 的支付方式开通项，逗号分隔；可空
 * @param pendingCapabilities 系统项：未 ENABLED 的支付方式开通项，逗号分隔；可空
 * @param channelRegistration 人工项：渠道报备已确认
 * @param integration 人工项：技术接入已确认
 * @param productionVerified 人工项：生产交易验证已完成
 * @param openIssuesCleared 人工项：未结事项已处理
 */
public record TierChecklist(String qualificationStatus, Instant qualificationValidTo, Integer underwritingVersionNo,
    Long agreementId, String agreementStatus, String enabledCapabilities, String pendingCapabilities,
    boolean channelRegistration, boolean integration, boolean productionVerified, boolean openIssuesCleared) {

    /** 资格已批准的状态值。 */
    public static final String QUALIFICATION_APPROVED = "APPROVED";

    /**
     * 清单系统项与人工项是否全部满足
     *
     * @param hasEffectiveUnderwriting 本库是否有有效承保（以本库事实为准，不信任调用方传入的承保坐标）
     * @return 资格 APPROVED、有有效承保、至少一个 ENABLED 开通项且四个人工项全部确认
     */
    public boolean satisfied(boolean hasEffectiveUnderwriting) {
        return QUALIFICATION_APPROVED.equals(qualificationStatus) && hasEffectiveUnderwriting && enabledCapabilities != null
            && !enabledCapabilities.isBlank() && channelRegistration && integration && productionVerified
            && openIssuesCleared;
    }

    /** 把清单写入变更记录的清单列。 */
    public void applyTo(ClientLimitTierChange row) {
        row.setChkQualificationStatus(qualificationStatus);
        row.setChkQualificationValidTo(qualificationValidTo);
        row.setChkUnderwritingVersionNo(underwritingVersionNo);
        row.setChkAgreementId(agreementId);
        row.setChkAgreementStatus(agreementStatus);
        row.setChkEnabledCapabilities(enabledCapabilities);
        row.setChkPendingCapabilities(pendingCapabilities);
        row.setChkChannelRegistration(channelRegistration);
        row.setChkIntegration(integration);
        row.setChkProductionVerified(productionVerified);
        row.setChkOpenIssuesCleared(openIssuesCleared);
    }

    /** 由变更记录的清单列构造；没有清单（资格状态为空）时为空。 */
    static TierChecklist of(ClientLimitTierChange row) {
        if (row.getChkQualificationStatus() == null) {
            return null;
        }
        return new TierChecklist(row.getChkQualificationStatus(), row.getChkQualificationValidTo(),
            row.getChkUnderwritingVersionNo(), row.getChkAgreementId(), row.getChkAgreementStatus(),
            row.getChkEnabledCapabilities(), row.getChkPendingCapabilities(), Boolean.TRUE.equals(row.getChkChannelRegistration()),
            Boolean.TRUE.equals(row.getChkIntegration()), Boolean.TRUE.equals(row.getChkProductionVerified()),
            Boolean.TRUE.equals(row.getChkOpenIssuesCleared()));
    }
}
