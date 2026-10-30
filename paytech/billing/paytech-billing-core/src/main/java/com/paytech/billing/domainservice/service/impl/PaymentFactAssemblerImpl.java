package com.paytech.billing.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.dataobject.BillingFactPricing;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;
import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.ChannelCostComponentAmount;
import com.paytech.billing.domain.ChannelCostResolution;
import com.paytech.billing.domain.CommercialPrice;
import com.paytech.billing.domain.FeeCalculationBasis;
import com.paytech.billing.domain.OrderCommercialContext;
import com.paytech.billing.domain.PaymentFactInputs;
import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.domain.OrderTaxCalculation;
import com.paytech.billing.domain.OrderTaxComponent;
import com.paytech.billing.domain.PriceComponentAmount;
import com.paytech.billing.domain.PriceSettlement;
import com.paytech.billing.domainservice.service.ChannelCostCalculator;
import com.paytech.billing.domainservice.service.CommercialPriceCalculator;
import com.paytech.billing.domainservice.service.PaymentFactAssembler;
import com.paytech.billing.domainservice.validator.fact.BillingFactInvariants;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * 自动 PAYMENT 事实组装：费用计算复用 CommercialPriceCalculator / ChannelCostCalculator 纯计算，其余为字段映射
 * <li>付方 / 收方（技术方案 4.6 行目录）：CLIENT_FEE、CLIENT_PRINCIPAL 由渠道（清算账户）付给 OE / Client；PARTNER_SHARE、
 * PLATFORM_SERVICE_CHARGE、CHANNEL_COST 由 OE 付给 Local Partner / 平台 Finance Entity / 渠道（清算账户）；全部 CHARGE、无目标余额桶。</li>
 * <li>基数：Client 定价只用毛额；Partner 两条款可引用 Σ CLIENT_FEE；渠道成本基数为毛额。</li>
 * <li>SELLER_TAX（技术方案 11.2）：订单 tax 计算为 READY 时，税额为正的组件各一行，由渠道（清算账户）付给 TAX_AUTHORITY（纳税主体 Party），
 * 规则族 TAX_RULE、版本为 tax 计算 ID；本金 = 毛额 − Σ CLIENT_FEE − Σ SELLER_TAX。无计算或 NOT_MANAGED 不生成税行。</li>
 * <li>Σ CLIENT_FEE 记账金额为零时业务拒绝（S14）：ledger STANDARD_TRANSACTION 的 PAYMENT_SUCCEEDED 指令把 CLIENT_FEE_REVENUE 标为必需行，
 * 没有手续费行的事实无法过账；其他组件的零金额仍只是不生成行（S7）。</li>
 * <li>计算证据 source_evidence 只含命中证据（{@value #EVIDENCE_SCHEMA_VERSION} 版），不含载荷全文。</li>
 */
@Service
@AllArgsConstructor
public class PaymentFactAssemblerImpl implements PaymentFactAssembler {
    /** 计算证据结构版本。 */
    static final int EVIDENCE_SCHEMA_VERSION = 1;

    private final IdGenerator idGenerator;
    private final ChannelCostCalculator channelCostCalculator;
    private final CommercialPriceCalculator commercialPriceCalculator;

    /** 头、行、引用与定价输入依次组装，最后按登记约束校验。 */
    @Override
    public BillingFactContext assemble(PaymentFactInputs inputs, Instant now) {
        BillingFact fact = buildFact(inputs, now);
        List<BillingFactLine> lines = new ArrayList<>();
        BigDecimal clientFee = addClientLines(fact, inputs, lines, now);
        if (inputs.getPayment().getOrder().hasLocalPartner()) {
            addPartnerLines(fact, inputs, clientFee, lines, now);
        }
        addChannelCostLines(fact, inputs, lines, now);
        BillingFactContext context = new BillingFactContext(fact, buildRefs(fact, inputs, now), lines, null,
            inputs.getMinorUnit(), buildPricing(fact, inputs, now));
        BillingFactInvariants.checkAutoPayment(context.toPayload());
        return context;
    }

    /** AUTO / APPROVED / 版本 1 的事实头；毛额为 capturedAmount，小数位不得超过币种精度。 */
    BillingFact buildFact(PaymentFactInputs inputs, Instant now) {
        OrderPaymentSucceededFact payment = inputs.getPayment();
        OrderCommercialContext order = payment.getOrder();
        if (payment.getCapturedAmount().stripTrailingZeros().scale() > inputs.getMinorUnit()) {
            throw new BizCheckFailException("支付成功金额超出币种精度: 支付尝试 " + payment.getPaymentAttemptId());
        }
        BillingFact fact = new BillingFact();
        fact.setId(idGenerator.nextId());
        fact.setFactType(BillingFactType.PAYMENT);
        fact.setOriginType(FactOriginType.AUTO);
        fact.setSourceSystem(OrderPaymentSucceededFact.SOURCE_SYSTEM);
        fact.setSourceEventKey(payment.sourceEventKey());
        fact.setSourceVersion(payment.getAggregateVersion());
        fact.setSourcePayloadHash(payment.getPayloadHash());
        fact.setClientId(order.getClientId());
        fact.setEntityNo(order.getEntityNo());
        fact.setLocalPartnerId(order.getLocalPartnerId());
        fact.setFinanceEntityId(inputs.getFinanceEntityId());
        fact.setCurrency(order.getCurrency());
        fact.setGrossAmount(payment.getCapturedAmount());
        fact.setBusinessTime(payment.getPaymentSucceededTime().truncatedTo(ChronoUnit.MICROS));
        fact.setBalanceAvailableTime(inputs.getBalanceAvailableTime());
        fact.setStatus(BillingFactStatus.APPROVED);
        fact.setFactVersion(1L);
        fact.setApprovedTime(now);
        fact.setCreateTime(now);
        fact.setUpdateTime(now);
        return fact;
    }

    /** CLIENT_FEE 各组件、SELLER_TAX 各组件与 CLIENT_PRINCIPAL；本金 = 毛额 − Σ CLIENT_FEE − Σ SELLER_TAX，必须为正。返回 Σ CLIENT_FEE。 */
    BigDecimal addClientLines(BillingFact fact, PaymentFactInputs inputs, List<BillingFactLine> lines, Instant now) {
        CommercialPrice price = inputs.getClientPrice();
        String clearingAccountId = clearingAccountId(inputs);
        List<PriceComponentAmount> fees = commercialPriceCalculator.calculate(price,
            FeeCalculationBasis.clientPricing(fact.getGrossAmount(), fact.getCurrency(), inputs.getMinorUnit()));
        BigDecimal feeTotal = fees.stream().map(PriceComponentAmount::getPostedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (feeTotal.signum() == 0) {
            throw new BizCheckFailException("Client 手续费合计为零，ledger STANDARD_TRANSACTION 要求 CLIENT_FEE_REVENUE 行，不能登记: 价格 "
                + price.getPriceId() + ", 来源 " + fact.getSourceEventKey());
        }
        for (PriceComponentAmount fee : nonZero(fees)) {
            lines.add(priceLine(withParties(newLine(fact, lines, FactComponentType.CLIENT_FEE, now),
                FactPartyRole.CHANNEL, clearingAccountId, FactPartyRole.OPERATING_ENTITY, fact.getEntityNo()),
                SourceRuleType.COMMERCIAL_PRICE, price, fee));
        }
        BigDecimal taxTotal = addSellerTaxLines(fact, inputs, lines, now);
        BigDecimal principal = fact.getGrossAmount().subtract(feeTotal).subtract(taxTotal);
        if (principal.signum() <= 0) {
            throw new BizCheckFailException("Client 手续费与税额之和不小于支付金额，本金不为正: 价格 " + price.getPriceId() + ", 来源 "
                + fact.getSourceEventKey());
        }
        lines.add(principalLine(withParties(newLine(fact, lines, FactComponentType.CLIENT_PRINCIPAL, now),
            FactPartyRole.CHANNEL, clearingAccountId, FactPartyRole.CLIENT, fact.getClientId().toString()),
            price, fact.getGrossAmount(), principal));
        return feeTotal;
    }

    /**
     * SELLER_TAX：订单 tax 计算为 READY 时每个税额为正的组件一行，零税额组件不生成行；返回 Σ SELLER_TAX
     * <li>组件税额小数位超过币种精度为上游数据不一致，业务拒绝。</li>
     */
    BigDecimal addSellerTaxLines(BillingFact fact, PaymentFactInputs inputs, List<BillingFactLine> lines, Instant now) {
        OrderTaxCalculation tax = inputs.getPayment().getOrder().getTaxCalculation();
        if (tax == null || !tax.collectsSellerTax()) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (OrderTaxComponent component : tax.getComponents()) {
            if (component.getTaxAmount().stripTrailingZeros().scale() > inputs.getMinorUnit()) {
                throw new BizCheckFailException("税务组件税额超出币种精度: 税务计算 " + tax.getTaxCalculationId() + ", 来源 "
                    + fact.getSourceEventKey());
            }
            if (component.getTaxAmount().signum() == 0) {
                continue;
            }
            lines.add(sellerTaxLine(withParties(newLine(fact, lines, FactComponentType.SELLER_TAX, now),
                FactPartyRole.CHANNEL, clearingAccountId(inputs), FactPartyRole.TAX_AUTHORITY,
                Long.toString(component.getTaxSubjectPartyId())), tax, component));
            taxTotal = taxTotal.add(component.getTaxAmount());
        }
        return taxTotal;
    }

    /** PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE；两条款可引用 Σ CLIENT_FEE。 */
    void addPartnerLines(BillingFact fact, PaymentFactInputs inputs, BigDecimal clientFee, List<BillingFactLine> lines,
        Instant now) {
        FeeCalculationBasis basis =
            new FeeCalculationBasis(fact.getGrossAmount(), clientFee, fact.getCurrency(), inputs.getMinorUnit());
        CommercialPrice share = inputs.getPartnerSharePrice();
        for (PriceComponentAmount amount : nonZero(commercialPriceCalculator.calculate(share, basis))) {
            lines.add(priceLine(withParties(newLine(fact, lines, FactComponentType.PARTNER_SHARE, now),
                FactPartyRole.OPERATING_ENTITY, fact.getEntityNo(), FactPartyRole.LOCAL_PARTNER,
                fact.getLocalPartnerId().toString()), SourceRuleType.PARTNER_SHARE, share, amount));
        }
        CommercialPrice charge = inputs.getServiceChargePrice();
        for (PriceComponentAmount amount : nonZero(commercialPriceCalculator.calculate(charge, basis))) {
            lines.add(priceLine(withParties(newLine(fact, lines, FactComponentType.PLATFORM_SERVICE_CHARGE, now),
                FactPartyRole.OPERATING_ENTITY, fact.getEntityNo(), FactPartyRole.FINANCE_ENTITY,
                fact.getFinanceEntityId().toString()), SourceRuleType.PLATFORM_SERVICE_CHARGE, charge, amount));
        }
    }

    /** CHANNEL_COST：渠道成本组件按毛额计算，零金额不生成行。 */
    void addChannelCostLines(BillingFact fact, PaymentFactInputs inputs, List<BillingFactLine> lines, Instant now) {
        ChannelCostResolution cost = inputs.getChannelCost();
        List<ChannelCostComponentAmount> amounts =
            channelCostCalculator.calculate(cost.getComponents(), fact.getGrossAmount(), inputs.getMinorUnit());
        for (ChannelCostComponentAmount amount : amounts) {
            if (amount.getPostedAmount().signum() == 0) {
                continue;
            }
            lines.add(channelCostLine(withParties(newLine(fact, lines, FactComponentType.CHANNEL_COST, now),
                FactPartyRole.OPERATING_ENTITY, fact.getEntityNo(), FactPartyRole.CHANNEL, clearingAccountId(inputs)),
                cost, amount));
        }
    }

    /** 记账金额非零的组件。 */
    static List<PriceComponentAmount> nonZero(List<PriceComponentAmount> amounts) {
        return amounts.stream().filter(amount -> amount.getPostedAmount().signum() != 0).toList();
    }

    /** 清算账户标识：渠道付方 / 收方与 CLEARING_ACCOUNT 引用。 */
    static String clearingAccountId(PaymentFactInputs inputs) {
        return Long.toString(inputs.getClearingAccount().getClearingAccountId());
    }

    /** 新行：行号从 1 连续，CHARGE，无目标余额桶。 */
    BillingFactLine newLine(BillingFact fact, List<BillingFactLine> lines, FactComponentType component, Instant now) {
        BillingFactLine line = new BillingFactLine();
        line.setId(idGenerator.nextId());
        line.setFactId(fact.getId());
        line.setLineNo(lines.size() + 1);
        line.setComponentType(component);
        line.setEffectDirection(FactLineDirection.CHARGE);
        line.setCreateTime(now);
        return line;
    }

    /** commercial 组件行：金额、规则族、版本 / 价格 / 组件 ID 与计算证据。 */
    static BillingFactLine priceLine(BillingFactLine line, SourceRuleType ruleType, CommercialPrice price,
        PriceComponentAmount amount) {
        line.setBasisAmount(amount.getBasisAmount());
        line.setRateValue(amount.getRateValue());
        line.setRawAmount(amount.getRawAmount());
        line.setPostedAmount(amount.getPostedAmount());
        line.setSourceRuleType(ruleType);
        line.setSourceRuleVersionId(price.getTermVersionId());
        line.setSourcePriceId(price.getPriceId());
        line.setSourceRuleComponentId(amount.getComponent().getComponentId());
        line.setSourceEvidence(componentEvidence(price, amount));
        return line;
    }

    /** 本金行：基数为毛额，金额为毛额 − Σ CLIENT_FEE，规则来源为 Client 价格（无组件）。 */
    static BillingFactLine principalLine(BillingFactLine line, CommercialPrice price, BigDecimal gross,
        BigDecimal principal) {
        line.setBasisAmount(gross);
        line.setRawAmount(principal);
        line.setPostedAmount(principal);
        line.setSourceRuleType(SourceRuleType.COMMERCIAL_PRICE);
        line.setSourceRuleVersionId(price.getTermVersionId());
        line.setSourcePriceId(price.getPriceId());
        line.setSourceEvidence(priceEvidence(price));
        return line;
    }

    /** 渠道成本行：规则族 CHANNEL_COST，版本为命中成本版本，无价格 ID。 */
    static BillingFactLine channelCostLine(BillingFactLine line, ChannelCostResolution cost,
        ChannelCostComponentAmount amount) {
        line.setBasisAmount(amount.getBasisAmount());
        line.setRateValue(amount.getRateValue());
        line.setRawAmount(amount.getRawAmount());
        line.setPostedAmount(amount.getPostedAmount());
        line.setSourceRuleType(SourceRuleType.CHANNEL_COST);
        line.setSourceRuleVersionId(cost.getCostVerId());
        line.setSourceRuleComponentId(amount.getComponent().getComponentId());
        line.setSourceEvidence(channelCostEvidence(cost, amount));
        return line;
    }

    /**
     * 税行：金额取组件税额；事件不携带组件税基，基数按资金类行记为金额本身、无费率
     * <li>规则族 TAX_RULE，版本为 tax 计算 ID，无价格与组件 ID（组件不可变标识在 tax 内由计算 ID + 辖区 + 注册定位）。</li>
     */
    static BillingFactLine sellerTaxLine(BillingFactLine line, OrderTaxCalculation tax, OrderTaxComponent component) {
        line.setBasisAmount(component.getTaxAmount());
        line.setRawAmount(component.getTaxAmount());
        line.setPostedAmount(component.getTaxAmount());
        line.setSourceRuleType(SourceRuleType.TAX_RULE);
        line.setSourceRuleVersionId(tax.getTaxCalculationId());
        line.setSourceEvidence(taxEvidence(tax, component));
        return line;
    }

    /** 税务证据：计算、计税范围、辖区、税种、注册与纳税主体。 */
    static Map<String, Object> taxEvidence(OrderTaxCalculation tax, OrderTaxComponent component) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", EVIDENCE_SCHEMA_VERSION);
        evidence.put("taxCalculationId", Long.toString(tax.getTaxCalculationId()));
        evidence.put("taxScopeKey", tax.getTaxScopeKey());
        evidence.put("jurisdictionCode", component.getJurisdictionCode());
        evidence.put("taxType", component.getTaxType());
        evidence.put("registrationId", Long.toString(component.getRegistrationId()));
        evidence.put("taxSubjectPartyId", Long.toString(component.getTaxSubjectPartyId()));
        return evidence;
    }

    /** 价格级证据：条款类型、方案、价格与定价画像。 */
    static Map<String, Object> priceEvidence(CommercialPrice price) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", EVIDENCE_SCHEMA_VERSION);
        evidence.put("termType", price.getTermType());
        evidence.put("planId", price.getPlanId());
        evidence.put("priceId", Long.toString(price.getPriceId()));
        evidence.put("pricingProfileCode", price.getPricingProfileCode());
        return evidence;
    }

    /** 组件级证据：价格级证据加组件、费率、固定金额、上下限、命中档位与舍入方式。 */
    static Map<String, Object> componentEvidence(CommercialPrice price, PriceComponentAmount amount) {
        Map<String, Object> evidence = priceEvidence(price);
        evidence.put("componentCode", amount.getComponent().getComponentCode());
        evidence.put("componentType", amount.getComponent().getComponentType());
        evidence.put("calculationMethod", amount.getCalculationMethod().getCode());
        evidence.put("calculationBase", amount.getComponent().getCalculationBase());
        evidence.put("triggerCode", amount.getComponent().getTriggerCode());
        evidence.put("rate", plain(amount.getComponent().getRate()));
        evidence.put("fixedAmount", plain(amount.getComponent().getFixedAmount()));
        evidence.put("minAmount", plain(amount.getComponent().getMinAmount()));
        evidence.put("maxAmount", plain(amount.getComponent().getMaxAmount()));
        evidence.put("bandId", amount.getBand() == null ? null : Long.toString(amount.getBand().getBandId()));
        evidence.put("roundingMode", AmountRoundingMode.HALF_UP.getCode());
        return evidence;
    }

    /** 渠道成本证据：方案、版本、范围、组件、费率、固定金额、上下限、舍入方式与版本内容摘要。 */
    static Map<String, Object> channelCostEvidence(ChannelCostResolution cost, ChannelCostComponentAmount amount) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", EVIDENCE_SCHEMA_VERSION);
        evidence.put("planCode", cost.getPlanCode());
        evidence.put("costVer", cost.getCostVer());
        evidence.put("scopeId", cost.getScopeId());
        evidence.put("componentCode", amount.getComponent().getComponentCode());
        evidence.put("componentType", amount.getComponent().getComponentType());
        evidence.put("calculationBase", amount.getComponent().getCalculationBase());
        evidence.put("rate", plain(amount.getComponent().getRateValue()));
        evidence.put("fixedAmount", plain(amount.getComponent().getFixedAmount()));
        evidence.put("minAmount", plain(amount.getComponent().getMinAmount()));
        evidence.put("maxAmount", plain(amount.getComponent().getMaxAmount()));
        evidence.put("roundingMode", amount.getComponent().getRoundingMode().getCode());
        evidence.put("contentHash", cost.getContentHash());
        return evidence;
    }

    /** 普通十进制文本；为空时为空。 */
    static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /**
     * 八类必需引用与可选税务引用，按引用类型代码升序；渠道交易引用带外部交易号类型与交易号
     * <li>有 tax 计算时记 TAX_CALCULATION（技术方案 11.2）；仅存量订单（无计算、有旧税务快照）记 TAX_SNAPSHOT。</li>
     */
    List<BillingFactRef> buildRefs(BillingFact fact, PaymentFactInputs inputs, Instant now) {
        OrderPaymentSucceededFact payment = inputs.getPayment();
        OrderCommercialContext order = payment.getOrder();
        List<BillingFactRef> refs = new ArrayList<>(List.of(
            ref(fact, FactRefType.ORDER, order.getOrderId(), now),
            ref(fact, FactRefType.ORDER_CONTEXT, order.getOrderContextId(), now),
            ref(fact, FactRefType.PAYMENT_INTENT, payment.getPaymentIntentId(), now),
            ref(fact, FactRefType.PAYMENT_ATTEMPT, payment.getPaymentAttemptId(), now),
            ref(fact, FactRefType.PAYMENT_CHANNEL_ACCOUNT, payment.getChannelAccountId(), now),
            ref(fact, FactRefType.MID, payment.getMidId(), now),
            ref(fact, FactRefType.CLEARING_ACCOUNT, inputs.getClearingAccount().getClearingAccountId(), now)));
        BillingFactRef channelRef = ref(fact, FactRefType.CHANNEL_TRANSACTION_REF, payment.getChannelTransactionRefId(), now);
        channelRef.setExternalReferenceType(payment.getChannelReferenceType());
        channelRef.setExternalTransactionId(payment.getChannelTransactionRef());
        refs.add(channelRef);
        if (order.getTaxCalculation() != null) {
            refs.add(ref(fact, FactRefType.TAX_CALCULATION, order.getTaxCalculation().getTaxCalculationId(), now));
        } else if (order.getFinanceTaxSnapshotId() != null) {
            refs.add(ref(fact, FactRefType.TAX_SNAPSHOT, order.getFinanceTaxSnapshotId(), now));
        }
        refs.sort(Comparator.comparing(ref -> ref.getRefType().getCode()));
        return refs;
    }

    /** 一条引用，标识为十进制字符串。 */
    BillingFactRef ref(BillingFact fact, FactRefType refType, long refId, Instant now) {
        BillingFactRef ref = new BillingFactRef();
        ref.setId(idGenerator.nextId());
        ref.setFactId(fact.getId());
        ref.setRefType(refType);
        ref.setRefId(Long.toString(refId));
        ref.setCreateTime(now);
        return ref;
    }

    /** 定价输入：订单冻结维度、支付方式与卡属性、Client 价格 ID 与结算四字段；3DS 类别本期无来源保持为空。 */
    static BillingFactPricing buildPricing(BillingFact fact, PaymentFactInputs inputs, Instant now) {
        OrderPaymentSucceededFact payment = inputs.getPayment();
        OrderCommercialContext order = payment.getOrder();
        PriceSettlement settlement = inputs.getClientPrice().getSettlement();
        BillingFactPricing pricing = new BillingFactPricing();
        pricing.setFactId(fact.getId());
        pricing.setClientAgreementId(order.getClientAgreementId());
        pricing.setPartnerAgreementOeId(order.getPartnerAgreementOperatingEntityId());
        pricing.setCommercialContextTime(order.getCommercialContextTime().truncatedTo(ChronoUnit.MICROS));
        pricing.setConsumerCountryCode(order.getConsumerCountryCode());
        pricing.setPaymentMethod(payment.getPaymentMethodCode());
        pricing.setIndustryCode(order.getIndustryCode());
        pricing.setMccCode(order.getMccCode());
        pricing.setCardNetwork(payment.getCardBrand());
        pricing.setFundingType(payment.getFundingType());
        pricing.setCardLevel(payment.getCardLevel());
        pricing.setCommercialPriceId(inputs.getClientPrice().getPriceId());
        pricing.setSettlementBusinessDays(settlement.getSettlementBusinessDays());
        pricing.setBusinessCalendarCode(settlement.getBusinessCalendarCode());
        pricing.setSettlementTimezone(settlement.getSettlementTimezone());
        pricing.setCutoffSecondOfDay(settlement.getCutoffSecondOfDay());
        pricing.setCreateTime(now);
        return pricing;
    }

    /** 写入付方与收方后返回同一明细行。 */
    static BillingFactLine withParties(BillingFactLine line, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId) {
        line.setPayerRole(payerRole);
        line.setPayerId(payerId);
        line.setPayeeRole(payeeRole);
        line.setPayeeId(payeeId);
        return line;
    }
}
