package com.paytech.risk.ext.rest.control;

import static com.paytech.risk.ext.rest.control.UnderwritingWireConverter.codeOf;
import static com.paytech.risk.ext.rest.control.UnderwritingWireConverter.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.ClientTierDetailView;
import com.paytech.risk.control.domain.limittier.ClientTierView;
import com.paytech.risk.control.domain.limittier.LimitTierStatistics;
import com.paytech.risk.control.domain.limittier.TierChecklist;
import com.paytech.risk.control.domain.limittier.TierClientProfile;
import com.paytech.risk.control.domain.limittier.TierMigrationResult;
import com.paytech.risk.control.domain.limittier.TierObservation;
import com.paytech.risk.control.domain.limittier.TierQuotaCatalog;
import com.paytech.risk.decision.api.control.request.TierChangeChecklistBody;
import com.paytech.risk.decision.api.control.request.TierMigrationClientBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChecklistBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierObservationBody;
import com.paytech.risk.decision.api.control.response.ClientTierPendingChangeBody;
import com.paytech.risk.decision.api.control.response.ClientTierQuotaBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.TierMigrationResponseBody;
import com.paytech.risk.decision.api.control.response.TierMigrationResultBody;
import com.paytech.risk.decision.api.control.response.TierQuotaCurrencyBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierQuotaTierBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;

/** 限额档位协议模型与 risk-control Owner 模型之间的转换；ID、金额与时间为字符串。 */
final class LimitTierWireConverter {
    /** 未配置币种放行。 */
    static final String POLICY_ALLOW = "ALLOW";

    /** 未配置币种拒绝。 */
    static final String POLICY_DENY = "DENY";

    /** 迁移新设档位。 */
    static final String MIGRATION_CREATED = "CREATED";

    /** 迁移跳过已有档位。 */
    static final String MIGRATION_SKIPPED = "SKIPPED_EXISTING";

    /** 纯转换工具。 */
    private LimitTierWireConverter() { }

    /** 上线复核清单请求转换；空清单保持为空。 */
    static TierChecklist checklist(TierChangeChecklistBody body) {
        if (body == null) {
            return null;
        }
        return new TierChecklist(body.getQualificationStatus(), validTo(body.getQualificationValidTo()),
            body.getUnderwritingVersionNo(), UnderwritingWireConverter.id(body.getAgreementId()), body.getAgreementStatus(),
            UnderwritingWireConverter.blankToNull(body.getEnabledPaymentCapabilities()),
            UnderwritingWireConverter.blankToNull(body.getPendingPaymentCapabilities()),
            Boolean.TRUE.equals(body.getChannelRegistrationConfirmed()), Boolean.TRUE.equals(body.getIntegrationConfirmed()),
            Boolean.TRUE.equals(body.getProductionVerified()), Boolean.TRUE.equals(body.getOpenIssuesCleared()));
    }

    /** 迁移 Client 转换；格式已由 Body 注解限定。 */
    static TierClientProfile client(TierMigrationClientBody body) {
        return new TierClientProfile(Long.parseLong(body.getClientId()), body.getClientDisplayName(), body.getClientNo(),
            UnderwritingWireConverter.code(UnderwritingClientType.values(), body.getClientType(), "clientType"),
            body.getJurisdictionCode());
    }

    /** 目标档位解析；格式已由 Body 注解限定。 */
    static LimitTier tier(String value, String field) {
        return UnderwritingWireConverter.code(LimitTier.values(), value, field);
    }

    /** 列表一行。 */
    static ClientTierSummaryBody summary(ClientTierView view) {
        ClientTierSummaryBody body = new ClientTierSummaryBody();
        fillSummary(body, view);
        return body;
    }

    /** 详情：列表字段、版本、受限前档位、观察期与按币种额度。 */
    static ClientTierDetailResponseBody detail(ClientTierDetailView detail) {
        ClientTierDetailResponseBody body = new ClientTierDetailResponseBody();
        ClientTierView view = detail.tier();
        fillSummary(body, view);
        body.setVersion(view.getVersion());
        body.setPreviousTier(codeOf(view.getPreviousTier()));
        body.setObservation(observation(view.getObservation()));
        body.setQuotas(detail.quotas().stream().map(LimitTierWireConverter::quota).toList());
        body.setUnconfiguredCurrencies(detail.unconfiguredCurrencies());
        body.setUnconfiguredCurrencyPolicy(detail.allowsUnconfiguredCurrency() == null ? null
            : detail.allowsUnconfiguredCurrency() ? POLICY_ALLOW : POLICY_DENY);
        return body;
    }

    /** 变更历史。 */
    static ClientTierChangeListResponseBody history(List<ClientTierChangeView> views) {
        ClientTierChangeListResponseBody body = new ClientTierChangeListResponseBody();
        body.setItems(views.stream().map(LimitTierWireConverter::change).toList());
        return body;
    }

    /** 一条变更记录。 */
    static ClientTierChangeResponseBody change(ClientTierChangeView view) {
        ClientTierChangeResponseBody body = new ClientTierChangeResponseBody();
        body.setChangeId(Long.toString(view.getChangeId()));
        body.setClientId(Long.toString(view.getClientId()));
        body.setChangeType(codeOf(view.getChangeType()));
        body.setFromTier(codeOf(view.getFromTier()));
        body.setToTier(codeOf(view.getToTier()));
        body.setStatus(codeOf(view.getStatus()));
        body.setApprovalLevel(codeOf(view.getApprovalLevel()));
        body.setCrrSnapshot(codeOf(view.getCrrSnapshot()));
        body.setUnderwritingId(text(view.getUnderwritingId()));
        body.setReason(view.getReason());
        body.setGoLiveNote(view.getGoLiveNote());
        body.setChecklist(checklist(view.getChecklist()));
        body.setObservationTargetDays(view.getObservationTargetDays());
        body.setObservationTargetTransactions(view.getObservationTargetTransactions());
        body.setObservationDaysAtRequest(view.getObservationDaysAtRequest());
        body.setObservationAllowedAtRequest(view.getObservationAllowedAtRequest());
        body.setRequestedBy(text(view.getRequestedBy()));
        body.setRequestedTime(text(view.getRequestedTime()));
        body.setDecidedBy(text(view.getDecidedBy()));
        body.setDecidedTime(text(view.getDecidedTime()));
        body.setDecisionComment(view.getDecisionComment());
        body.setVersion(view.getVersion());
        return body;
    }

    /** 四档 × 币种额度与配置问题。 */
    static TierQuotaListResponseBody quotas(TierQuotaCatalog catalog) {
        TierQuotaListResponseBody body = new TierQuotaListResponseBody();
        body.setTiers(List.of(LimitTier.VERIFICATION, LimitTier.OBSERVATION, LimitTier.STANDARD, LimitTier.RESTRICTED)
            .stream().map(tier -> quotaTier(tier, catalog)).toList());
        body.setConfigurationIssues(catalog.issues());
        return body;
    }

    /** 列表顶部统计。 */
    static TierStatisticsResponseBody statistics(LimitTierStatistics statistics) {
        TierStatisticsResponseBody body = new TierStatisticsResponseBody();
        body.setVerification(statistics.verification());
        body.setObservation(statistics.observation());
        body.setStandard(statistics.standard());
        body.setRestricted(statistics.restricted());
        body.setPendingApproval(statistics.pendingApproval());
        body.setObservationDue(statistics.observationDue());
        return body;
    }

    /** 迁移结果。 */
    static TierMigrationResponseBody migration(List<TierMigrationResult> results) {
        TierMigrationResponseBody body = new TierMigrationResponseBody();
        body.setResults(results.stream().map(result -> {
            TierMigrationResultBody item = new TierMigrationResultBody();
            item.setClientId(Long.toString(result.clientId()));
            item.setOutcome(result.created() ? MIGRATION_CREATED : MIGRATION_SKIPPED);
            return item;
        }).toList());
        return body;
    }

    /** 填写列表与详情共有的字段。 */
    static void fillSummary(ClientTierSummaryBody body, ClientTierView view) {
        TierObservation observation = view.getObservation();
        body.setClientId(Long.toString(view.getClientId()));
        body.setClientDisplayName(view.getClientDisplayName());
        body.setClientNo(view.getClientNo());
        body.setJurisdictionCode(view.getJurisdictionCode());
        body.setClientType(codeOf(view.getClientType()));
        body.setCrr(codeOf(view.getCrr()));
        body.setTier(codeOf(view.getTier()));
        body.setEffectiveTime(text(view.getEffectiveTime()));
        body.setSourceType(codeOf(view.getSourceType()));
        body.setSourceReason(view.getSourceReason());
        body.setObservationStartedTime(observation == null ? null : observation.startedTime().toString());
        body.setObservationDaysElapsed(observation == null ? null : observation.daysElapsed());
        body.setObservationTargetDays(observation == null ? null : observation.targetDays());
        body.setObservationAllowedTransactions(observation == null ? null : observation.allowedTransactions());
        body.setObservationTargetTransactions(observation == null ? null : observation.targetTransactions());
        body.setObservationDue(observation != null && observation.due());
        body.setPendingChange(pending(view.getPendingChange()));
        body.setUpdatedAt(text(view.getUpdatedAt()));
    }

    /** 待审批申请摘要。 */
    static ClientTierPendingChangeBody pending(ClientTierChangeView view) {
        if (view == null) {
            return null;
        }
        ClientTierPendingChangeBody body = new ClientTierPendingChangeBody();
        body.setChangeId(Long.toString(view.getChangeId()));
        body.setFromTier(codeOf(view.getFromTier()));
        body.setToTier(codeOf(view.getToTier()));
        body.setApprovalLevel(codeOf(view.getApprovalLevel()));
        body.setRequestedBy(text(view.getRequestedBy()));
        body.setRequestedTime(text(view.getRequestedTime()));
        return body;
    }

    /** 观察期进度。 */
    static ClientTierObservationBody observation(TierObservation observation) {
        if (observation == null) {
            return null;
        }
        ClientTierObservationBody body = new ClientTierObservationBody();
        body.setStartedTime(observation.startedTime().toString());
        body.setDaysElapsed(observation.daysElapsed());
        body.setTargetDays(observation.targetDays());
        body.setAllowedTransactions(observation.allowedTransactions());
        body.setTargetTransactions(observation.targetTransactions());
        body.setDue(observation.due());
        return body;
    }

    /** 一个币种的额度与已用。 */
    static ClientTierQuotaBody quota(ClientTierDetailView.QuotaUsage usage) {
        ClientTierQuotaBody body = new ClientTierQuotaBody();
        body.setCurrency(usage.currency());
        body.setSingle(money(usage.single(), usage.currency()));
        body.setDay(money(usage.day(), usage.currency()));
        body.setLast30Days(money(usage.last30Days(), usage.currency()));
        body.setUsedToday(money(usage.usedToday(), usage.currency()));
        body.setUsedLast30Days(money(usage.usedLast30Days(), usage.currency()));
        return body;
    }

    /** 一个档位的币种额度。 */
    static TierQuotaTierBody quotaTier(LimitTier tier, TierQuotaCatalog catalog) {
        TierQuotaTierBody body = new TierQuotaTierBody();
        body.setTier(tier.getCode());
        body.setCurrencies(catalog.quotas(tier).stream().map(quota -> {
            TierQuotaCurrencyBody item = new TierQuotaCurrencyBody();
            item.setCurrency(quota.currency());
            item.setSingle(money(quota.single(), quota.currency()));
            item.setDay(money(quota.day(), quota.currency()));
            item.setLast30Days(money(quota.month(), quota.currency()));
            return item;
        }).toList());
        body.setUnconfiguredCurrencyPolicy(tier.allowsUnconfiguredCurrency() ? POLICY_ALLOW : POLICY_DENY);
        return body;
    }

    /** 申请时冻结的清单。 */
    static ClientTierChecklistBody checklist(TierChecklist checklist) {
        if (checklist == null) {
            return null;
        }
        ClientTierChecklistBody body = new ClientTierChecklistBody();
        body.setQualificationStatus(checklist.qualificationStatus());
        body.setQualificationValidTo(text(checklist.qualificationValidTo()));
        body.setUnderwritingVersionNo(checklist.underwritingVersionNo());
        body.setAgreementId(text(checklist.agreementId()));
        body.setAgreementStatus(checklist.agreementStatus());
        body.setEnabledPaymentCapabilities(checklist.enabledCapabilities());
        body.setPendingPaymentCapabilities(checklist.pendingCapabilities());
        body.setChannelRegistrationConfirmed(checklist.channelRegistration());
        body.setIntegrationConfirmed(checklist.integration());
        body.setProductionVerified(checklist.productionVerified());
        body.setOpenIssuesCleared(checklist.openIssuesCleared());
        return body;
    }

    /** 金额按币种精度补齐小数位（USD / AED 为 2 位），不舍入多出的有效位；未知币种按 2 位。 */
    static String money(BigDecimal value, String currency) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        int digits = fractionDigits(currency);
        return (stripped.scale() < digits ? stripped.setScale(digits, RoundingMode.UNNECESSARY) : stripped).toPlainString();
    }

    /** 币种的默认小数位；无法识别时为 2。 */
    static int fractionDigits(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? 2 : digits;
        } catch (IllegalArgumentException unknown) {
            return 2;
        }
    }

    /** 资格有效期解析；格式已由 Body 注解限定，日期越界仍按参数错误。 */
    static Instant validTo(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("checklist.qualificationValidTo 不是合法的 UTC 时间");
        }
    }
}
