package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.MAKER_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.test.base.AbstractEconomicFactApiTest;
import com.paytech.billing.test.builder.EconomicFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 提交经济事实（DRAFT）：从真实 HTTP 入口经 Processor、Service、Repository 写入本地 mor_billing
 * <li>覆盖新提交、同来源引用同内容重放与改内容拒绝、合作方三元组与平台服务费主体不变量、币种启用与精度、目录外代码，以及成本调整的
 * 来源支付事实要求。提交不登记发布任务。</li>
 *
 * @see EconomicFactCommandApi#submitEconomicFact(com.paytech.billing.api.request.SubmitEconomicFactRequestBody)
 */
class SubmitEconomicFactTest extends AbstractEconomicFactApiTest {

    /** 新提交：201 DRAFT、版本 0、明细行按币种精度补齐；详情查询一致，未登记发布任务。 */
    @Test
    void test_normalFlow() {
        EconomicFactRequestBuilder builder = EconomicFactRequestBuilder.template()
            .addLine(AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                "1.5", FactLineDirection.CHARGE);

        BillingResponseChecker response = submit(builder).assertSuccess(201)
            .assertText("factType", EconomicsType.MANUAL_ADJUSTMENT.getCode())
            .assertText("status", EconomicFactStatus.DRAFT.getCode()).assertText("factVersion", "0")
            .assertText("sourceReference", builder.sourceReference()).assertText("grossAmount", "11.50")
            .assertText("makerId", MAKER_ID).assertText("checkerId", null).assertText("publishedAt", null);
        long factId = response.id("factId");
        JsonNode lines = response.json().path("lines");

        assertAll(() -> assertEquals(2, lines.size()), () -> assertEquals("1", lines.get(0).path("lineNo").asString()),
            () -> assertEquals("10.00", lines.get(0).path("amount").asString()),
            () -> assertEquals(FactPartyRole.OPERATING_ENTITY.getCode(), lines.get(0).path("payerRole").asString()),
            () -> assertEquals(CLIENT_ID, lines.get(0).path("payeeId").asString()),
            () -> assertEquals("1.50", lines.get(1).path("amount").asString()),
            () -> assertEquals(AmountSource.CLIENT_FEE.getCode(), lines.get(1).path("feeTypeCode").asString()));
        assertEquals(response.json(), getFact(factId).assertSuccess(200).json());
        assertEquals(2, testMapper.countEconomicFactLines(factId));
        events.assertNoEnvelope(factId);
    }

    /** 同来源引用同内容重复提交返回原事实（200），改内容拒绝；都不新增事实。 */
    @Test
    void test_replayBySourceReference() {
        EconomicFactRequestBuilder builder = EconomicFactRequestBuilder.template();
        long factId = submitDraft(builder);

        submit(builder).assertSuccess(200).assertText("factId", Long.toString(factId))
            .assertText("status", EconomicFactStatus.DRAFT.getCode());
        submit(EconomicFactRequestBuilder.template().sourceReference(builder.sourceReference()).clearLines()
            .addLine(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT_ID, "10.01", FactLineDirection.CHARGE))
            .assertBusinessRejected("来源引用已登记且内容不同");

        assertEquals(1, testMapper.countEconomicFactLines(factId));
    }

    /** 合作方三元组必须全有或全无；缺三元组时不能登记合作方分润。 */
    @Test
    void test_partnerTripleInvariant() {
        submit(EconomicFactRequestBuilder.template().partner(LOCAL_PARTNER_ID, null, FINANCE_ENTITY_ID))
            .assertBusinessRejected("Partner 三元组不完整");
        submit(EconomicFactRequestBuilder.template().clearLines().addLine(AmountSource.PARTNER_SHARE,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID, "3.00",
            FactLineDirection.CHARGE)).assertBusinessRejected("缺少合作方三元组");
    }

    /** 平台服务费收方必须是头部 Finance Entity；完整三元组且收方一致时登记成功。 */
    @Test
    void test_serviceChargeSubjectInvariant() {
        submit(EconomicFactRequestBuilder.template().partner().clearLines().addLine(
            AmountSource.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID, FactPartyRole.PLATFORM,
            FINANCE_ENTITY_ID, "2.00", FactLineDirection.CHARGE)).assertBusinessRejected("平台服务费收方必须是 Finance Entity");
        submit(EconomicFactRequestBuilder.template().partner().clearLines().addLine(
            AmountSource.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, "529999", "2.00", FactLineDirection.CHARGE))
            .assertBusinessRejected("收方与头部主体不一致");

        submit(EconomicFactRequestBuilder.template().partner().clearLines().addLine(
            AmountSource.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, FINANCE_ENTITY_ID, "2.00", FactLineDirection.CHARGE))
            .assertSuccess(201).assertText("financeEntityId", FINANCE_ENTITY_ID);
    }

    /** 币种必须在只读副本中启用，金额精度不得超过币种 minor unit（不隐式舍入）。 */
    @Test
    void test_currencyPrecision() {
        submit(EconomicFactRequestBuilder.template().currency("EUR")).assertBusinessRejected("币种未配置或未启用");
        submit(EconomicFactRequestBuilder.template().clearLines().addLine(AmountSource.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "10.001",
            FactLineDirection.CHARGE)).assertBusinessRejected("超过币种精度 2 位");
    }

    /** 目录外代码、GROSS_AMOUNT 明细、零金额与付方等于收方都在保存前拒绝。 */
    @Test
    void test_invalidParameter() {
        submit(EconomicFactRequestBuilder.template().addRawLine("UNKNOWN_FEE", FactPartyRole.CLIENT.getCode(), CLIENT_ID,
            FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, "1.00", FactLineDirection.CHARGE.getCode()))
            .assertInvalidRequest();
        submit(EconomicFactRequestBuilder.template().addRawLine(AmountSource.CLIENT_FEE.getCode(), "MERCHANT", CLIENT_ID,
            FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, "1.00", FactLineDirection.CHARGE.getCode()))
            .assertInvalidRequest();
        submit(EconomicFactRequestBuilder.template().clearLines().addLine(AmountSource.GROSS_AMOUNT,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "1.00", FactLineDirection.CHARGE))
            .assertInvalidRequest();
        submit(EconomicFactRequestBuilder.template().clearLines().addLine(AmountSource.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "0.00", FactLineDirection.CHARGE))
            .assertBusinessRejected("金额必须为正");
        submit(EconomicFactRequestBuilder.template().clearLines().addLine(AmountSource.CLIENT_PRINCIPAL,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.CLIENT, CLIENT_ID, "1.00", FactLineDirection.CHARGE))
            .assertBusinessRejected("付方与收方不能相同");
    }

    /** 成本调整必须指定已批准的同主体 PAYMENT 来源事实与渠道清算账户，且只登记 CHANNEL_COST。 */
    @Test
    void test_costAdjustmentAnchors() {
        long paymentFactId = 2_000_001L;
        testMapper.insertEconomicFact(paymentFactId, EconomicsType.PAYMENT.getCode(), "PAY-2000001", Long.parseLong(CLIENT_ID),
            ENTITY_NO, null, CURRENCY, EconomicFactStatus.APPROVED.getCode(), 1, null);
        EconomicFactRequestBuilder cost = costAdjustment().parentFact(paymentFactId).channelAccount("610001");

        submit(costAdjustment().channelAccount("610001")).assertBusinessRejected("成本调整必须指定来源支付事实与渠道清算账户");
        submit(costAdjustment().parentFact(paymentFactId)).assertBusinessRejected("成本调整必须指定来源支付事实与渠道清算账户");
        submit(costAdjustment().parentFact(paymentFactId).channelAccount("610001").client("300002"))
            .assertBusinessRejected("来源事实不存在、不是已批准的同主体同币种支付事实");
        submit(costAdjustment().parentFact(paymentFactId).channelAccount("610001").addLine(AmountSource.CLIENT_FEE,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "1.00", FactLineDirection.CHARGE))
            .assertBusinessRejected("成本调整只能登记 CHANNEL_COST");

        submit(cost).assertSuccess(201).assertText("factType", EconomicsType.COST_ADJUSTMENT.getCode())
            .assertText("parentFactId", Long.toString(paymentFactId)).assertText("channelAccountId", "610001");
        assertNull(factRow(paymentFactId).get("published_at"));
    }

    /** OE 向渠道多付 0.40 USD 渠道成本的成本调整模板（来源事实与渠道清算账户由用例给出）。 */
    static EconomicFactRequestBuilder costAdjustment() {
        return EconomicFactRequestBuilder.template().factType(EconomicsType.COST_ADJUSTMENT).clearLines().addLine(
            AmountSource.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL, "710001", "0.40",
            FactLineDirection.ADJUSTMENT);
    }
}
