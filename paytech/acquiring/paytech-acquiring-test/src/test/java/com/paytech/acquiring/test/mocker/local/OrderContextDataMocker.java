package com.paytech.acquiring.test.mocker.local;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.core.dal.mapper.order.OrderTaxCalculationMapper;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.acquiring.test.builder.checkout.CreateCheckoutContextBuilder;
import com.paytech.acquiring.test.dal.CheckoutPaymentTestMapper;
import com.paytech.acquiring.test.dal.OrderQueryTestMapper;

/** 订单冻结上下文的前置事实直接入本地库；订单身份由 CheckoutDataMocker 预留并统一清理。 */
@TestComponent
public class OrderContextDataMocker {
    /** Partner 场景的合作方与 CCT 版本样例，与平台来源默认值互不重叠。 */
    public static final long LOCAL_PARTNER_ID = 701L;

    public static final long PARTNER_AGREEMENT_ID = 702L;

    public static final long PARTNER_AGREEMENT_OE_ID = 703L;

    public static final long PARTNER_SHARE_VERSION_ID = 704L;

    public static final long PLATFORM_CHARGE_VERSION_ID = 705L;

    public static final long PLATFORM_SERVICE_PARTY_ID = 706L;

    private final CheckoutDataMocker ids;
    private final OrderQueryTestMapper orders;
    private final CheckoutPaymentTestMapper contexts;
    private final OrderTaxCalculationMapper taxCalculations;
    private final TransactionTemplate transaction;

    /** 数据准备使用短事务，HTTP 请求在该事务提交之后执行。 */
    public OrderContextDataMocker(CheckoutDataMocker ids, OrderQueryTestMapper orders,
        CheckoutPaymentTestMapper contexts, OrderTaxCalculationMapper taxCalculations,
        PlatformTransactionManager manager) {
        this.ids = ids;
        this.orders = orders;
        this.contexts = contexts;
        this.taxCalculations = taxCalculations;
        this.transaction = new TransactionTemplate(manager);
    }

    /** 预留一个本库主键，由全局清理按订单回收。 */
    public long nextId() {
        return ids.reserveOrderId();
    }

    /** 追加订单的税务计算记录（税务服务上线后的订单支付所依据的报价）。 */
    public void mockTaxCalculation(OrderTaxCalculation row) {
        if (taxCalculations.insert(row) != 1) {
            throw new IllegalStateException("前置税务计算未写入");
        }
    }

    /** 每例独立的平台来源订单上下文，Partner 字段全部为空。 */
    public CreateCheckoutContext template() {
        var context = CreateCheckoutContextBuilder.template().orderId(ids.reserveOrderId()).clientId(ids.clientId())
            .build();
        context.getOrder().setClientOrderId("context-" + context.getOrder().getOrderId());
        return context;
    }

    /** 把模板改为 Partner 来源：订单、上下文与法律事实的 Partner 成组字段同时非空。 */
    public CreateCheckoutContext partnerTemplate() {
        var context = template();
        context.getOrder().setEntitySourceType(EntitySourceType.PARTNER.getCode());
        context.getOrder().setLocalPartnerId(LOCAL_PARTNER_ID);
        context.getFrozen().partnerAgreementId(PARTNER_AGREEMENT_ID)
            .partnerAgreementOperatingEntityId(PARTNER_AGREEMENT_OE_ID).partnerShareVersionId(PARTNER_SHARE_VERSION_ID)
            .platformChargeVersionId(PLATFORM_CHARGE_VERSION_ID).platformServicePartyId(PLATFORM_SERVICE_PARTY_ID);
        return context;
    }

    /** 插入订单及冻结上下文与法律事实；不插入会话、商品或消费者合同等无关事实。 */
    public void mock(CreateCheckoutContext context) {
        transaction.executeWithoutResult(status -> {
            insertOrder(context);
            contexts.insertContext(context.getFrozen());
            contexts.insertLegal(context.getFrozen());
        });
    }

    /** 只插入订单，模拟尚未完成支付准备的订单。 */
    public void mockOrderOnly(CreateCheckoutContext context) {
        transaction.executeWithoutResult(status -> insertOrder(context));
    }

    /** 只插入订单与上下文，模拟法律事实缺失的损坏数据。 */
    public void mockWithoutLegal(CreateCheckoutContext context) {
        transaction.executeWithoutResult(status -> {
            insertOrder(context);
            contexts.insertContext(context.getFrozen());
        });
    }

    /** 基础创建列与支付准备补齐的 OE 来源列分两步写入，与生产两阶段写入一致。 */
    private void insertOrder(CreateCheckoutContext context) {
        orders.insertOrder(context.getOrder());
        if (orders.updateEntity(context.getOrder()) != 1) {
            throw new IllegalStateException("前置订单未写入");
        }
    }
}
