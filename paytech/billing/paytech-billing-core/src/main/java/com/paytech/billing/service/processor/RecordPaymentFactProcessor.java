package com.paytech.billing.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.enums.OrderTaxStatus;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.ChannelCostResolution;
import com.paytech.billing.domain.ClearingAccount;
import com.paytech.billing.domain.CommercialPrice;
import com.paytech.billing.domain.OrderCommercialContext;
import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.domain.OrderTaxCalculation;
import com.paytech.billing.domain.PaymentFactInputs;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.domainservice.repository.BillingFactPricingRepository;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.service.BillingFactService;
import com.paytech.billing.domainservice.service.CurrencyRuleService;
import com.paytech.billing.domainservice.service.PaymentFactAssembler;
import com.paytech.billing.domainservice.service.SettlementTimeCalculator;
import com.paytech.billing.integration.channelconnector.ChannelCostClient;
import com.paytech.billing.integration.commercial.CommercialPriceClient;
import com.paytech.billing.integration.ledger.FinanceEntityClient;
import com.paytech.billing.integration.reconciliation.ClearingAccountClient;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 按 acquiring OrderPaymentSucceeded 登记自动 PAYMENT 计费事实（技术方案 4.1、4.3 S13、4.5）
 * <li>业务唯一身份为 source_system = acquiring + source_event_key = PAYMENT_ATTEMPT:&lt;attemptId&gt;：已登记且来源载荷 Hash 相同返回原事实，
 * 不同则业务拒绝；版本不进入幂等键（S3）。</li>
 * <li>订单冻结商业字段随事件到达（领域层不调用流程层，D08）；新登记在事务外依次读取：commercial Client 定价 →（有 Local Partner）分润、
 * 平台服务费与 Finance Entity → channel-connector 渠道成本 → reconciliation 清算账户；随后纯内存交叉校验、费用计算、T+N 与组装，最后一个
 * 本地事务写入事实、定价输入、引用、明细行并登记 BillingFactRecorded 发布任务。</li>
 * <li>税务随事件到达、不读 tax：READY 计算按组件生成 SELLER_TAX 行并记 TAX_CALCULATION 引用；存量订单只记 TAX_SNAPSHOT 引用（技术方案 11.2）。</li>
 * <li>不加资源锁：同一事件的并发登记由 uk_fact_source 兜底，冲突转为并发冲突，由消费方重投后命中原事实。</li>
 * <li>事件内部或与上游事实不一致、定价不可表达为业务拒绝（BizCheckFailException）；上游未就绪、日历或结算字段缺失为
 * ErrorException，事实停在重试（技术方案 4.8）。</li>
 */
@Component
@AllArgsConstructor
public class RecordPaymentFactProcessor extends AbstractProcessor<OrderPaymentSucceededFact, BillingFactView> {
    private final Clock clock;
    private final ChannelCostClient channelCostClient;
    private final BillingFactService billingFactService;
    private final CurrencyRuleService currencyRuleService;
    private final FinanceEntityClient financeEntityClient;
    private final PaymentFactAssembler paymentFactAssembler;
    private final BillingFactRepository billingFactRepository;
    private final ClearingAccountClient clearingAccountClient;
    private final CommercialPriceClient commercialPriceClient;
    private final SettlementTimeCalculator settlementTimeCalculator;
    private final BillingFactPricingRepository billingFactPricingRepository;

    /** 渠道交易引用三项（引用 ID、引用类型、外部交易号）必须齐全（S4），否则 reconciliation 不能匹配。 */
    @Override
    protected void validate(OrderPaymentSucceededFact payment) {
        if (!payment.channelReferenceComplete()) {
            throw new BizCheckFailException("订单支付成功事件缺少渠道交易引用: 支付尝试 " + payment.getPaymentAttemptId());
        }
    }

    @Override
    protected BillingFactView processRequest(OrderPaymentSucceededFact payment) {
        // 查询原登记
        BillingFactView original = getOriginalFact(payment);
        if (original != null) {
            return original;
        }

        // 读取上游事实
        PaymentFactInputs inputs = loadInputs(payment);

        // 构建上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BillingFactContext context = paymentFactAssembler.assemble(inputs, now);

        // 保存上下文
        billingFactService.recordAutoFact(context);

        // 响应处理
        return BillingFactView.of(context, context.getPricing(), List.of());
    }

    /** 同一支付尝试已登记时比较来源载荷 Hash：相同返回原事实，不同拒绝；未登记返回空。 */
    BillingFactView getOriginalFact(OrderPaymentSucceededFact payment) {
        BillingFact original = billingFactRepository.findBySource(OrderPaymentSucceededFact.SOURCE_SYSTEM,
            payment.sourceEventKey());
        if (original == null) {
            return null;
        }
        if (!original.getSourcePayloadHash().equals(payment.getPayloadHash())) {
            throw new BizCheckFailException("支付尝试已登记计费事实且事件内容不同: " + payment.sourceEventKey() + ", 原事实 "
                + original.getId());
        }
        BillingFactContext context = billingFactService.loadContext(original.getId());
        return BillingFactView.of(context, billingFactPricingRepository.findByFactId(original.getId()), List.of());
    }

    /** 校验事件后在事务外读取全部上游事实并交叉校验，再推算币种精度与到期转可用时间。 */
    PaymentFactInputs loadInputs(OrderPaymentSucceededFact payment) {
        checkOrderFacts(payment);
        CommercialPrice clientPrice = commercialPriceClient.resolveClientPricing(payment);
        PaymentFactInputs.PaymentFactInputsBuilder inputs = PaymentFactInputs.builder().payment(payment)
            .clientPrice(clientPrice);
        if (payment.getOrder().hasLocalPartner()) {
            loadPartnerTerms(inputs, payment);
        }
        ChannelCostResolution channelCost = channelCostClient.resolve(payment);
        ClearingAccount clearingAccount = clearingAccountClient.resolve(payment.getMidId(),
            payment.getOrder().getCurrency());
        checkChannelFacts(payment, channelCost, clearingAccount);
        return inputs.channelCost(channelCost).clearingAccount(clearingAccount).minorUnit(minorUnitOf(payment))
            .balanceAvailableTime(settlementTimeCalculator.balanceAvailableTime(payment.getPaymentSucceededTime(),
                clientPrice.getSettlement()))
            .build();
    }

    /** Partner 分润、平台服务费（同一 Partner 合同下的两个冻结版本）与平台签约 Party 对应的 Finance Entity。 */
    void loadPartnerTerms(PaymentFactInputs.PaymentFactInputsBuilder inputs, OrderPaymentSucceededFact payment) {
        OrderCommercialContext order = payment.getOrder();
        inputs.partnerSharePrice(commercialPriceClient.resolvePartnerTerm(CommercialCodes.TermType.PARTNER_SHARE,
            order.getPartnerShareVersionId(), payment));
        inputs.serviceChargePrice(commercialPriceClient.resolvePartnerTerm(
            CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE, order.getPlatformChargeVersionId(), payment));
        inputs.financeEntityId(financeEntityClient.resolveFinanceEntityId(order.getPlatformServicePartyId()));
    }

    /**
     * 事件内的订单与支付事实可用于登记：收取金额等于订单金额、消费者国家已冻结、Local Partner 来源的 Partner 五项齐全、tax 计算一致
     * <li>不满足为数据不一致，业务拒绝（重试无意义）。</li>
     */
    void checkOrderFacts(OrderPaymentSucceededFact payment) {
        OrderCommercialContext order = payment.getOrder();
        if (order.getOrderAmount().compareTo(payment.getCapturedAmount()) != 0) {
            throw new BizCheckFailException("支付收取金额与订单金额不一致: 支付尝试 " + payment.getPaymentAttemptId()
                + ", 订单 " + order.getOrderId());
        }
        if (order.getConsumerCountryCode() == null) {
            throw new BizCheckFailException("订单冻结上下文缺少消费者国家: 订单 " + order.getOrderId());
        }
        if (order.hasLocalPartner() && !order.partnerTermsComplete()) {
            throw new BizCheckFailException("Local Partner 订单缺少 Partner 合同、OE 关联、条款版本或平台签约 Party: 订单 "
                + order.getOrderId());
        }
        checkTaxFacts(order);
    }

    /**
     * 订单的 tax 计算可用于登记（技术方案 11.2）
     * <li>无计算（存量订单）或 NOT_MANAGED 不生成税行，不再校验；已支付订单不应是 BLOCKED。</li>
     * <li>READY：税额合计必填且等于 Σ 组件税额；税额为正的组件必须带税务注册。</li>
     */
    void checkTaxFacts(OrderCommercialContext order) {
        OrderTaxCalculation tax = order.getTaxCalculation();
        if (tax == null || tax.getTaxStatus() == OrderTaxStatus.NOT_MANAGED) {
            return;
        }
        String context = ": 订单 " + order.getOrderId() + ", 税务计算 " + tax.getTaxCalculationId();
        if (tax.getTaxStatus() != OrderTaxStatus.READY) {
            throw new BizCheckFailException("已支付订单的税务计算状态不是 READY 或 NOT_MANAGED: " + tax.getTaxStatus().getCode()
                + context);
        }
        if (tax.getTaxAmount() == null || tax.getTaxAmount().compareTo(tax.componentTotal()) != 0) {
            throw new BizCheckFailException("税额合计与税务组件之和不一致" + context);
        }
        boolean unregistered = tax.getComponents().stream()
            .anyMatch(component -> component.getTaxAmount().signum() > 0 && component.getRegistrationId() == null);
        if (unregistered) {
            throw new BizCheckFailException("征税的税务组件缺少税务注册" + context);
        }
    }

    /** 渠道成本币种、清算账户的 OE 与币种必须与订单一致，否则数据不一致。 */
    void checkChannelFacts(OrderPaymentSucceededFact payment, ChannelCostResolution channelCost,
        ClearingAccount clearingAccount) {
        OrderCommercialContext order = payment.getOrder();
        boolean consistent = Objects.equals(channelCost.getCurrency(), order.getCurrency())
            && Objects.equals(clearingAccount.getEntityNo(), order.getEntityNo())
            && Objects.equals(clearingAccount.getCurrency(), order.getCurrency());
        if (!consistent) {
            throw new BizCheckFailException("渠道成本或清算账户与订单不一致（OE 或币种）: 支付尝试 "
                + payment.getPaymentAttemptId() + ", 清算账户 " + clearingAccount.getClearingAccountId());
        }
    }

    /** 事实币种精度；快照暂缺该币种（刷新未完成）时失败重试，而不是拒绝事件。 */
    int minorUnitOf(OrderPaymentSucceededFact payment) {
        try {
            return currencyRuleService.requireUsable(payment.getOrder().getCurrency()).getMinorUnit();
        } catch (BizCheckFailException unavailable) {
            throw new ErrorException("币种规则暂不可用，稍后重试: " + payment.getOrder().getCurrency(), unavailable);
        }
    }
}
