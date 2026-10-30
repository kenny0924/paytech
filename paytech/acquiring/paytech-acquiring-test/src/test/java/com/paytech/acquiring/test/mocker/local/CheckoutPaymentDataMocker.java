package com.paytech.acquiring.test.mocker.local;

import com.paytech.acquiring.core.dal.dataobject.order.OrderRelationshipBinding;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.core.dal.mapper.order.OrderRelationshipBindingMapper;
import com.paytech.acquiring.core.dal.mapper.order.OrderTaxCalculationMapper;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder;
import com.paytech.acquiring.test.builder.checkout.CreateCheckoutContextBuilder;
import com.paytech.acquiring.test.dal.*;
import com.paytech.foundation.token.PublicTokenGenerator;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 自有订单由 CheckoutDataMocker 统一清理；不调用被测业务制造 Arrange。 */
@TestComponent
public class CheckoutPaymentDataMocker {
    /** 税务订单模板的单行净额（USD）。 */
    public static final String TAXED_NET = "100.00";

    private final CheckoutDataMocker ids;
    private final OrderQueryTestMapper orders;
    private final CheckoutPaymentTestMapper mapper;
    private final OrderTaxCalculationMapper taxCalculations;
    private final OrderRelationshipBindingMapper bindings;
    private final TransactionTemplate transaction;

    public CheckoutPaymentDataMocker(
            CheckoutDataMocker ids,
            OrderQueryTestMapper orders,
            CheckoutPaymentTestMapper mapper,
            OrderTaxCalculationMapper taxCalculations,
            OrderRelationshipBindingMapper bindings,
            PlatformTransactionManager manager) {
        this.ids = ids;
        this.orders = orders;
        this.mapper = mapper;
        this.taxCalculations = taxCalculations;
        this.bindings = bindings;
        this.transaction = new TransactionTemplate(manager);
    }

    public CreateCheckoutContext mockPrepared(boolean consumer) {
        var now = Instant.parse("2026-09-08T00:00:00Z");
        var context =
                CreateCheckoutContextBuilder.template()
                        .orderId(ids.reserveOrderId())
                        .clientId(ids.clientId())
                        .now(now)
                        .build();
        context.getSession()
                .setPublicToken(
                        "chk_"
                                + PublicTokenGenerator.generate(
                                        Long.toString(context.getOrder().getOrderId())));
        context.getOrder().setClientOrderId("intent-" + context.getOrder().getOrderId());
        transaction.executeWithoutResult(
                status -> {
                    orders.insertOrder(context.getOrder());
                    mapper.completeOrder(context.getOrder().getOrderId());
                    orders.insertSession(context.getSession());
                    context.getItems().forEach(orders::insertItem);
                    orders.insertRefund(context.getRefundPosition());
                    orders.insertFunds(context.getFundsAvailability());
                    mapper.insertContext(context.getFrozen());
                    mapper.insertLegal(context.getFrozen());
                    mapper.insertContract(context.getFrozen());
                    if (consumer) {
                        mapper.insertConsumer(context.getOrder().getOrderId(), now);
                    }
                });
        return context;
    }

    /**
     * 税务服务上线后的订单模板：单行净额 {@value #TAXED_NET}，尚未写库；用例据订单 ID 准备计算记录后调用
     * {@link #mockTaxed(CreateCheckoutContext, boolean, long, List)}。
     */
    public CreateCheckoutContext taxedTemplate() {
        var now = Instant.parse("2026-09-08T00:00:00Z");
        var context = CreateCheckoutContextBuilder.template().orderId(ids.reserveOrderId()).clientId(ids.clientId())
            .now(now).build();
        var net = new BigDecimal(TAXED_NET).setScale(8);
        context.getSession().setPublicToken(
            "chk_" + PublicTokenGenerator.generate(Long.toString(context.getOrder().getOrderId())));
        context.getOrder().setClientOrderId("taxed-" + context.getOrder().getOrderId());
        context.getOrder().setMerchandiseAmount(net);
        context.getOrder().setNetAmount(net);
        context.getItems().forEach(item -> item.unitAmount(net).lineGrossAmount(net).lineNetAmount(net));
        context.getFrozen().financeTaxSnapshotId(null).financeTaxSnapshotHash(null);
        return context;
    }

    /** 本库预留的新主键，由全局清理按订单回收。 */
    public long nextId() {
        return ids.reserveOrderId();
    }

    /**
     * 写入税务服务上线后的订单形态
     * <li>依次追加计算记录，订单当前报价取最后一条；上下文的建单计算取第一条；合同绑定 contractCalculationId（Mock 合同文件 ID 同计算 ID）。</li>
     * <li>商业关系为 MOR 8 行绑定，TAX_RESPONSIBLE 为 OE Party {@value CheckoutContextResponseBuilder#OE_PARTY_ID}。</li>
     */
    public CreateCheckoutContext mockTaxed(CreateCheckoutContext context, boolean consumer, long contractCalculationId,
        List<OrderTaxCalculation> calculations) {
        return mockTaxed(context, consumer, true, contractCalculationId, calculations);
    }

    /** 同上；snapshot=false 时不写商业关系角色绑定（CLIENT 来源 OE，commercial 不推导快照）。 */
    public CreateCheckoutContext mockTaxed(CreateCheckoutContext context, boolean consumer, boolean snapshot,
        long contractCalculationId, List<OrderTaxCalculation> calculations) {
        var order = context.getOrder();
        var current = calculations.getLast();
        order.setTaxAmount(current.getTaxAmount());
        order.setRoundingAmount(current.getRoundingAmount());
        order.setTotalAmount(current.getTotalAmount());
        order.setAmountStatus(current.amountStatus());
        context.getFrozen().initialTaxCalculationId(calculations.getFirst().getTaxCalculationId())
            .contractTaxCalculationId(contractCalculationId).renderedFileId(contractCalculationId);
        var now = order.getCreateTime();
        var rows = CheckoutContextResponseBuilder.morBindings(order.getClientId()).stream()
            .map(binding -> OrderRelationshipBinding.builder().bindingId(ids.reserveOrderId()).orderId(order.getOrderId())
                .roleCode(binding.getRole()).subjectType(binding.getSubjectType()).subjectId(binding.getSubjectId())
                .partyId(binding.getPartyId() == null ? null : Long.valueOf(binding.getPartyId()))
                .snapshotModelVersion(CheckoutContextResponseBuilder.SNAPSHOT_MODEL_VERSION).createTime(now).build())
            .toList();
        transaction.executeWithoutResult(status -> {
            insertBase(context, consumer, now);
            calculations.forEach(taxCalculations::insert);
            if (snapshot) {
                bindings.insertAll(rows);
            }
        });
        return context;
    }

    /** 订单、会话、商品、初始投影、上下文、法律事实与合同。 */
    private void insertBase(CreateCheckoutContext context, boolean consumer, Instant now) {
        orders.insertOrder(context.getOrder());
        mapper.completeOrder(context.getOrder().getOrderId());
        orders.insertSession(context.getSession());
        context.getItems().forEach(orders::insertItem);
        orders.insertRefund(context.getRefundPosition());
        orders.insertFunds(context.getFundsAvailability());
        mapper.insertContext(context.getFrozen());
        mapper.insertLegal(context.getFrozen());
        mapper.insertContract(context.getFrozen());
        if (consumer) {
            mapper.insertConsumer(context.getOrder().getOrderId(), now);
        }
    }

    public void changeVersion(long orderId) {
        mapper.changeVersion(orderId);
    }
}
