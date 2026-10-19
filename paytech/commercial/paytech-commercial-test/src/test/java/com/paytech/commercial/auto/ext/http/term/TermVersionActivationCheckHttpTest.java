package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 激活校验（technique.md §2.6）每条一个失败例，经排期 / 激活接口触发；失败 400 且版本保持原状态
 * <li>1 合同 ACTIVE 与区间、2 画像 ACTIVE、3 开通项 × 币种覆盖完整、4 档位连续与本期取值限制、5 区间不重叠；以及生效起点未到不能激活。</li>
 *
 * @see CommercialTermApi#scheduleVersion(String, com.paytech.commercial.api.term.request.ScheduleCommercialTermVersionRequestBody)
 */
class TermVersionActivationCheckHttpTest extends AbstractCommercialTermApiTest {
    private static final String BIZ = CommonErrorCode.BUSINESS_CHECK_FAILED.getCode();

    /** 1：合同仍是 SIGNED（未 ACTIVE）时排期失败。 */
    @Test
    void test_check1_agreementNotActive() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.SIGNED));
        mockCardProfiles(PricingProfileStatus.ACTIVE);

        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))));

        assertRejected(approvedVersion, NOW, "激活校验 1");
    }

    /** 2：价格引用的画像仍是 DRAFT（写入时只要求存在）。 */
    @Test
    void test_check2_profileNotActive() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE));
        mockCardProfiles(PricingProfileStatus.DRAFT);

        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))));

        assertRejected(approvedVersion, NOW, "激活校验 2");
    }

    /** 3：CARD_VISA 开通 USD 与 AED，只有 USD 价格，AED 覆盖缺口。 */
    @Test
    void test_check3_coverageGap() {
        catalog.mockSeedCatalog();
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE));
        mockCardProfiles(PricingProfileStatus.ACTIVE);
        subscriptions.mockCapability(AgreementCapabilityBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID)
            .entityNo(ENTITY_NO)).currencies("USD", "AED");

        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD_VISA, 1, percentage("MDR", "0.025"))));

        assertRejected(approvedVersion, NOW, "激活校验 3（覆盖完整）：开通项没有命中的价格: AE993/CARD_VISA/AED");
    }

    /** 3：只有 VISA + CREDIT 价格时，未知资金类型的 VISA 交易无价可依，同样是覆盖缺口。 */
    @Test
    void test_check3_onlyNarrowProfile() {
        mockBaseline();

        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD_VISA_CREDIT, 1, percentage("MDR", "0.025"))));

        assertRejected(approvedVersion, NOW, "激活校验 3");
    }

    /** 4：阶梯档位不连续（第 1 档上界 100、第 2 档下界 200）。 */
    @Test
    void test_check4_bandGap() {
        mockBaseline();

        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()), price("USD", CARD, 1,
            tiered("TIER", "USD", BandBasis.TRANSACTION_AMOUNT, "WHOLE_BASE", band("0", "100", "0.03"),
                band("200", null, "0.02")))));

        assertRejected(approvedVersion, NOW, "激活校验 4（组件与档位）：档位不连续或重叠");
    }

    /** 4：本期拒绝 PERIOD_VOLUME + RETROACTIVE 阶梯与 INTERCHANGE_PLUS_PLUS 定价模型（写入允许，排期拒绝）。 */
    @Test
    void test_check4_sliceRestrictions() {
        mockBaseline();
        JsonNode plan = createPlan();
        JsonNode periodVolume = approved(replacePrices(createVersion(plan), price("USD", CARD, 1,
            tiered("TIER", "USD", BandBasis.PERIOD_VOLUME, "RETROACTIVE", band("0", null, "0.02")))));
        assertRejected(periodVolume, NOW, "本期阶梯基数只接受 TRANSACTION_AMOUNT");

        CreateCommercialTermVersionRequestBody icpp = new CreateCommercialTermVersionRequestBody();
        icpp.setExpectedChangeVersion(text(periodVolume, "changeVersion"));
        icpp.setPricingModel(PricingModel.INTERCHANGE_PLUS_PLUS.getCode());
        icpp.setFeeTaxTreatment("EXCLUSIVE");
        JsonNode icppVersion = ok(201, http.post(TERMS + "/plans/" + text(plan, "planId") + "/versions", headers(MAKER), icpp));
        JsonNode approvedIcpp = approved(replacePrices(icppVersion, price("USD", CARD, 1, percentage("MDR", "0.025"))));

        assertRejected(approvedIcpp, NOW, "本期定价模型只接受 BLENDED");
    }

    /** 4：组件币种与价格币种不一致。 */
    @Test
    void test_check4_componentCurrency() {
        mockBaseline();

        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"), fixed("FEE", "1.10", "AED"))));

        assertRejected(approvedVersion, NOW, "组件币种与价格币种不一致");
    }

    /** 5：第 2 版排期起点不晚于已生效第 1 版的起点，区间重叠。 */
    @Test
    void test_check5_overlap() {
        mockBaseline();
        JsonNode active = activeVersion(price("USD", CARD, 1, percentage("MDR", "0.025")));
        JsonNode second = ok(201, createVersionResult(text(active, "planId"), text(active, "changeVersion"),
            text(active, "versionId")));

        JsonNode approvedSecond = approved(second);

        // 生效起点等于第 1 版起点（不早于当前时间但与 ACTIVE 重叠）
        assertRejected(approvedSecond, NOW, "激活校验 5（区间不重叠）");
    }

    /** 5：已有 SCHEDULED 版本（窗口未封闭）时再排期第 3 版被拒；第 2 版排期已把第 1 版窗口终点写成其起点、第 1 版仍 ACTIVE。 */
    @Test
    void test_check5_secondSchedule() {
        mockBaseline();
        JsonNode active = activeVersion(price("USD", CARD, 1, percentage("MDR", "0.025")));
        Instant secondFrom = NOW.plus(1, ChronoUnit.DAYS);
        JsonNode second = ok(201, createVersionResult(text(active, "planId"), text(active, "changeVersion"),
            text(active, "versionId")));
        JsonNode scheduledSecond = ok(200, scheduleResult(approved(second), secondFrom));
        JsonNode third = ok(201, createVersionResult(text(active, "planId"), text(scheduledSecond, "changeVersion"),
            text(active, "versionId")));

        JsonNode approvedThird = approved(third);

        var first = testMapper.termVersion(Long.parseLong(text(active, "versionId")));
        assertAll(() -> assertEquals("ACTIVE", first.get("status")),
            () -> assertEquals(secondFrom, ((java.time.LocalDateTime)first.get("effective_to")).toInstant(java.time.ZoneOffset.UTC)));
        assertRejected(approvedThird, NOW.plus(2, ChronoUnit.DAYS), "激活校验 5（区间不重叠）");
    }

    /** 1：排期在未来，激活时生效起点未到 400，版本保持 SCHEDULED；排期起点早于当前时间 400。 */
    @Test
    void test_check1_notYetEffective() {
        mockBaseline();
        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))));
        assertRejected(approvedVersion, NOW.minus(1, ChronoUnit.SECONDS), "不能早于当前时间");

        Instant future = NOW.plus(1, ChronoUnit.DAYS);
        JsonNode scheduled = ok(200, scheduleResult(approvedVersion, future));

        assertProblem(commandResult(scheduled, "activate", CHECKER), 400, BIZ, "生效起点未到");
        assertEquals("SCHEDULED",
            testMapper.termVersion(Long.parseLong(text(scheduled, "versionId"))).get("status"));
    }

    /** 排期失败 400，消息含校验编号，版本仍为 APPROVED。 */
    void assertRejected(JsonNode approvedVersion, Instant effectiveFrom, String detail) {
        assertAll(() -> assertProblem(scheduleResult(approvedVersion, effectiveFrom), 400, BIZ, detail),
            () -> assertEquals("APPROVED",
                testMapper.termVersion(Long.parseLong(text(approvedVersion, "versionId"))).get("status")));
    }
}
