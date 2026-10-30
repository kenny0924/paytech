package com.paytech.risk.control.domain.limittier;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;

import lombok.Getter;

/**
 * Client 档位查询结果：档位行、当前有效承保 CRR、来源变更理由、观察期进度与待审批申请的组合
 * （t_client_limit_tier + t_client_limit_tier_change + t_client_underwriting + 放行事件计数）
 * <li>无档位时只有 clientId 与可选的 crr，其余为空。</li>
 */
@Getter
public final class ClientTierView {
    /** Client ID。 */
    private final long clientId;

    /** 建档时 Client 名称。 */
    private final String clientDisplayName;

    /** 建档时 Client 编号。 */
    private final String clientNo;

    /** 建档时辖区代码。 */
    private final String jurisdictionCode;

    /** 建档时 Client 类型。 */
    private final UnderwritingClientType clientType;

    /** 当前有效承保 CRR；可空。 */
    private final UnderwritingRiskLevel crr;

    /** 当前档位；无档位为空。 */
    private final LimitTier tier;

    /** 进入 RESTRICTED 前的档位；可空。 */
    private final LimitTier previousTier;

    /** 当前档位生效时间。 */
    private final Instant effectiveTime;

    /** 当前档位来源。 */
    private final LimitTierSource sourceType;

    /** 产生当前档位的变更理由；可空。 */
    private final String sourceReason;

    /** 观察期进度；非 OBSERVATION 为空。 */
    private final TierObservation observation;

    /** 待审批申请；可空。 */
    private final ClientTierChangeView pendingChange;

    /** 档位行更新时间。 */
    private final Instant updatedAt;

    /** 档位并发版本；无档位为空。 */
    private final Integer version;

    /** 组合构造；tier 为空表示无档位。 */
    private ClientTierView(long clientId, ClientLimitTier tier, UnderwritingRiskLevel crr, String sourceReason,
        TierObservation observation, ClientTierChangeView pendingChange) {
        this.clientId = clientId;
        this.crr = crr;
        this.sourceReason = sourceReason;
        this.observation = observation;
        this.pendingChange = pendingChange;
        this.clientDisplayName = tier == null ? null : tier.getClientDisplayName();
        this.clientNo = tier == null ? null : tier.getClientNo();
        this.jurisdictionCode = tier == null ? null : tier.getJurisdictionCode();
        this.clientType = tier == null ? null : tier.getClientType();
        this.tier = tier == null ? null : tier.getTier();
        this.previousTier = tier == null ? null : tier.getPreviousTier();
        this.effectiveTime = tier == null ? null : tier.getEffectiveTime();
        this.sourceType = tier == null ? null : tier.getSourceType();
        this.updatedAt = tier == null ? null : tier.getUpdateTime();
        this.version = tier == null ? null : tier.getVersion();
    }

    /**
     * 构造查询结果
     *
     * @param tier 档位行
     * @param crr 当前有效承保 CRR；可空
     * @param sourceReason 来源变更理由；可空
     * @param observation 观察期进度；可空
     * @param pendingChange 待审批申请；可空
     * @return 查询结果
     */
    public static ClientTierView of(ClientLimitTier tier, UnderwritingRiskLevel crr, String sourceReason,
        TierObservation observation, ClientTierChangeView pendingChange) {
        return new ClientTierView(tier.getClientId(), tier, crr, sourceReason, observation, pendingChange);
    }

    /** 无档位的 Client。 */
    public static ClientTierView none(long clientId, UnderwritingRiskLevel crr) {
        return new ClientTierView(clientId, null, crr, null, null, null);
    }
}
