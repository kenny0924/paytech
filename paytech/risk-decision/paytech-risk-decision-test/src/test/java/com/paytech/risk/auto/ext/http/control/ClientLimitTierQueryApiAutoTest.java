package com.paytech.risk.auto.ext.http.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierApprovalLevel;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.api.control.ClientLimitTierApi;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.test.base.AbstractLimitTierApiTest;
import com.paytech.risk.test.builder.control.LimitTierRequestBuilder;
import com.paytech.risk.test.builder.control.TierQuotaVariableBuilder;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.LimitTierResponseChecker;
import com.paytech.risk.test.mapper.evaluation.PaymentRiskTestMapper;

import tools.jackson.databind.JsonNode;

/**
 * 档位查询：列表筛选与统计、详情的观察期进度与按币种额度 / 已用、四档额度解析与配置问题
 * <li>已用读该 Client 的支付放行日汇总（UTC 当日、近 30 个 UTC 日含当日）；结账放行、其他 Client 与 30 日外不计入；不同币种分别累计。</li>
 * <li>观察期笔数只计该 Client 观察期内的 PAYMENT_ATTEMPT_ALLOWED 事件；评估事件不计入。</li>
 * <li>额度由 TIER_ 前缀风控常量按命名约定解析；常量币种来源可为支付币种或与名称一致的固定币种。</li>
 *
 * @see ClientLimitTierApi#listClientTiers(String, String, String, Boolean, Boolean, String, Integer, Integer)
 * @see ClientLimitTierApi#getTierStatistics(String, String)
 * @see ClientLimitTierApi#getClientTier(String, String, String)
 * @see ClientLimitTierApi#listTierQuotas(String, String)
 */
class ClientLimitTierQueryApiAutoTest extends AbstractLimitTierApiTest {
    /** 历史事件 ID 起点；每例结束整表清理。 */
    private static final AtomicLong EVENTS = new AtomicLong(7_400_000_000L);

    @Autowired
    private PaymentRiskTestMapper events;

    /** 五档 Client 与一个待审批申请：全部、档位、到期、待审批、关键字筛选与统计。 */
    @Test
    void test_normalFlow_listFiltersAndStatistics() {
        long verification = newClientId();
        approved(verification, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        long due = newClientId();
        tierMocker.mockObservation(due, 31, 30, 100);
        long observing = newClientId();
        tierMocker.mockObservation(observing, 5, 30, 100);
        long standard = newClientId();
        tierMocker.mockTier(standard, LimitTier.STANDARD);
        long restricted = newClientId();
        tierMocker.mockRestricted(restricted, LimitTier.OBSERVATION);
        tierPost(REQUESTER, TIERS + "/clients/" + standard + "/tier-changes",
            LimitTierRequestBuilder.request(1, LimitTier.RESTRICTED)).assertStatus(201);

        LimitTierResponseChecker all = list("");
        all.assertStatus(200);
        assertEquals(5, all.node("/total").asLong(), "total");
        JsonNode verificationRow = row(all, verification);
        assertAll("验证档行",
            () -> assertEquals(UnderwritingRiskLevel.LOW.getCode(), verificationRow.path("crr").asString()),
            () -> assertEquals("承保批准", verificationRow.path("sourceReason").asString()),
            () -> assertEquals("Acme Trading LLC", verificationRow.path("clientDisplayName").asString()),
            () -> assertTrue(verificationRow.path("observationAllowedTransactions").isNull()));
        list("?tier=" + LimitTier.OBSERVATION.getCode()).assertPage(2, "tier",
            List.of(LimitTier.OBSERVATION.getCode(), LimitTier.OBSERVATION.getCode()));
        list("?observationDue=true").assertPage(1, "clientId", List.of(Long.toString(due)))
            .assertText("/items/0/observationDue", "true").assertText("/items/0/observationDaysElapsed", "31");
        list("?pendingApproval=true").assertPage(1, "clientId", List.of(Long.toString(standard)))
            .assertText("/items/0/pendingChange/toTier", LimitTier.RESTRICTED.getCode())
            .assertText("/items/0/pendingChange/approvalLevel", LimitTierApprovalLevel.ELEVATED.getCode())
            .assertText("/items/0/pendingChange/requestedBy", Long.toString(REQUESTER));
        list("?keyword=CL-T-" + observing).assertPage(1, "clientId", List.of(Long.toString(observing)));
        list("?keyword=" + restricted).assertPage(1, "clientId", List.of(Long.toString(restricted)));
        assertEquals(4, list("?keyword=Tier&size=20").node("/total").asLong(), "名称包含：四个前置档位 Client");
        list("?tier=PLATINUM").assertProblem(400, "INVALID_REQUEST");

        tierGet(REQUESTER, TIERS + "/tier-statistics").assertStatus(200)
            .assertText("/verification", "1").assertText("/observation", "2").assertText("/standard", "1")
            .assertText("/restricted", "1").assertText("/pendingApproval", "1").assertText("/observationDue", "1");
    }

    /** 观察档详情：按币种额度与 UTC 当日 / 近 30 个 UTC 日已用（读日放行汇总）、未配置币种与策略、观察期笔数到达目标即到期。 */
    @Test
    void test_normalFlow_detailQuotaUsageAndObservation() {
        long clientId = newClientId();
        tierMocker.mockObservation(clientId, 10, 30, 3);
        configureQuotas();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        daily(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, "USD", today, "100");
        daily(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, "USD", today.minusDays(29), "50");
        daily(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, "USD", today.minusDays(30), "70");
        daily(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, "AED", today, "30.5");
        daily(clientId, VelocityEventCode.CHECKOUT_ALLOWED, "USD", today, "999");
        daily(newClientId(), VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, "USD", today, "500");
        event(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, now.minus(1, ChronoUnit.HOURS), "100", "USD");
        event(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, now.minus(2, ChronoUnit.DAYS), "50", "USD");
        event(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, now.minus(3, ChronoUnit.HOURS), "30.5", "AED");
        event(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, now.minus(11, ChronoUnit.DAYS), "70", "USD");
        event(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minus(1, ChronoUnit.HOURS), "999", "USD");

        tierGet(REQUESTER, TIERS + "/clients/" + clientId + "/tier").assertStatus(200)
            .assertText("/tier", LimitTier.OBSERVATION.getCode())
            .assertText("/version", "1")
            .assertText("/observation/daysElapsed", "10")
            .assertText("/observation/allowedTransactions", "3")
            .assertText("/observation/targetTransactions", "3")
            .assertText("/observation/due", "true")
            .assertText("/observationDue", "true")
            .assertText("/quotas/0/currency", "AED")
            .assertText("/quotas/0/single", "5000.00")
            .assertText("/quotas/0/usedToday", "30.50")
            .assertText("/quotas/0/usedLast30Days", "30.50")
            .assertText("/quotas/1/currency", "USD")
            .assertText("/quotas/1/single", "2000.00")
            .assertText("/quotas/1/day", "20000.00")
            .assertText("/quotas/1/last30Days", "200000.00")
            .assertText("/quotas/1/usedToday", "100.00")
            .assertText("/quotas/1/usedLast30Days", "150.00")
            .assertText("/quotas/2/currency", null)
            .assertText("/unconfiguredCurrencies/0", "SAR")
            .assertText("/unconfiguredCurrencyPolicy", "DENY");
        tierGet(REQUESTER, TIERS + "/client-tiers?observationDue=true").assertPage(0, "clientId", List.of());
    }

    /** 四档额度：按命名约定归档，STANDARD 未配置币种放行；命名不规范、集合外币种与缺项进入配置问题。 */
    @Test
    void test_edgeCase_quotaCatalogIssues() {
        configureQuotas();
        createVariable(TierQuotaVariableBuilder.quota("TIER_STANDARD_USD_SINGLE", "50000"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_STANDARD_USD_DAY", "500000"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_STANDARD_USD_MONTH", "5000000"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_FOO_USD_SINGLE", "1"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_OBSERVATION_EUR_SINGLE", "10"));
        createVariable(TierQuotaVariableBuilder.fixedQuota("TIER_OBSERVATION_SAR_SINGLE", "AED", "10"));

        LimitTierResponseChecker quotas = tierGet(REQUESTER, TIERS + "/tier-quotas").assertStatus(200)
            .assertText("/tiers/0/tier", LimitTier.VERIFICATION.getCode())
            .assertText("/tiers/0/currencies/0/currency", "SAR")
            .assertText("/tiers/0/currencies/0/last30Days", null)
            .assertText("/tiers/0/unconfiguredCurrencyPolicy", "DENY")
            .assertText("/tiers/1/currencies/1/currency", "USD")
            .assertText("/tiers/2/tier", LimitTier.STANDARD.getCode())
            .assertText("/tiers/2/currencies/0/single", "50000.00")
            .assertText("/tiers/2/unconfiguredCurrencyPolicy", "ALLOW")
            .assertText("/tiers/3/tier", LimitTier.RESTRICTED.getCode())
            .assertText("/tiers/3/currencies/0", null)
            .assertText("/tiers/3/unconfiguredCurrencyPolicy", "DENY");
        List<String> issues = new ArrayList<>();
        quotas.node("/configurationIssues").forEach(issue -> issues.add(issue.asString()));
        assertEquals(List.of("无法识别的档位额度常量: TIER_FOO_USD_SINGLE",
            "TIER_OBSERVATION_SAR_SINGLE 须为币种 SAR（或输入币种）的非负金额常量",
            "未配置 TIER_VERIFICATION_SAR_MONTH",
            "TIER_OBSERVATION_EUR_* 的币种不在 TIER_OBSERVATION_CURRENCIES"), issues);
    }

    /**
     * 配置示例额度：OBSERVATION 的 USD（支付币种来源）与 AED（固定币种）全套、集合 {USD, AED}；
     * VERIFICATION 的 SAR 缺 MONTH、集合 {SAR}
     */
    private void configureQuotas() {
        createVariable(TierQuotaVariableBuilder.quota("TIER_OBSERVATION_USD_SINGLE", "2000"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_OBSERVATION_USD_DAY", "20000"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_OBSERVATION_USD_MONTH", "200000"));
        createVariable(TierQuotaVariableBuilder.fixedQuota("TIER_OBSERVATION_AED_SINGLE", "AED", "5000"));
        createVariable(TierQuotaVariableBuilder.fixedQuota("TIER_OBSERVATION_AED_DAY", "AED", "50000"));
        createVariable(TierQuotaVariableBuilder.fixedQuota("TIER_OBSERVATION_AED_MONTH", "AED", "500000"));
        createVariable(TierQuotaVariableBuilder.currencySet("TIER_OBSERVATION_CURRENCIES"));
        replaceMembers("TIER_OBSERVATION_CURRENCIES", TierQuotaVariableBuilder.currencies("USD", "AED"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_VERIFICATION_SAR_SINGLE", "100"));
        createVariable(TierQuotaVariableBuilder.quota("TIER_VERIFICATION_SAR_DAY", "500"));
        createVariable(TierQuotaVariableBuilder.currencySet("TIER_VERIFICATION_CURRENCIES"));
        replaceMembers("TIER_VERIFICATION_CURRENCIES", TierQuotaVariableBuilder.currencies("SAR"));
    }

    /** 插入一条锚定 Client 的累计事件。 */
    private void event(long clientId, VelocityEventCode code, Instant occurred, String amount, String currency) {
        long id = EVENTS.getAndIncrement();
        events.insertVelocityEvent(id, code.getCode(), occurred, new BigDecimal(amount), currency,
            Map.of(RiskSubjectType.CLIENT.getCode(), Long.toString(clientId)), id * 10);
    }

    /** 插入一条 Client 日放行汇总。 */
    private void daily(long clientId, VelocityEventCode code, String currency, LocalDate date, String amount) {
        tierStore.insertDailyTotal(EVENTS.getAndIncrement(), clientId, code.getCode(), currency, date, new BigDecimal(amount), 1);
    }

    /** 以申请人身份查询列表。 */
    private LimitTierResponseChecker list(String query) {
        return tierGet(REQUESTER, TIERS + "/client-tiers" + query);
    }

    /** 列表中指定 Client 的行。 */
    private static JsonNode row(LimitTierResponseChecker page, long clientId) {
        for (JsonNode item : page.node("/items")) {
            if (item.path("clientId").asString().equals(Long.toString(clientId))) {
                return item;
            }
        }
        throw new AssertionError("列表中没有 Client " + clientId);
    }
}
