package com.paytech.acquiring.service.processor.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.checkout.CheckoutInitializationContext;
import com.paytech.acquiring.domain.checkout.CheckoutPublicOrigin;
import com.paytech.acquiring.domain.checkout.CheckoutPublicToken;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.InitializationRequest;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxParties;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxQuoteRequest;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.order.CommerceDecimalRules;
import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCommercialPort;
import com.paytech.acquiring.domainservice.port.customer.CheckoutDocumentPort;
import com.paytech.acquiring.domainservice.port.customer.CheckoutRiskPort;
import com.paytech.acquiring.domainservice.port.customer.CheckoutTaxPort;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.service.checkout.CheckoutCreationService;
import com.paytech.acquiring.integration.counterparty.CounterpartyCustomerClient;
import com.paytech.acquiring.service.processor.checkout.request.CreateCheckoutRequest;
import com.paytech.acquiring.service.processor.checkout.response.CreateCheckoutResponse;
import com.paytech.counterparty.api.customer.ConsumerReferencePolicy;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.token.PublicTokenGenerator;
import com.paytech.tax.api.enums.TaxCalculationStage;

import lombok.AllArgsConstructor;

/** 创建并初始化 Checkout；外部事实在事务外取得，订单与初始化结果一次提交。 */
@Component
@AllArgsConstructor
public final class CreateCheckoutProcessor extends AbstractProcessor<CreateCheckoutRequest, CreateCheckoutResponse> {
    /** 订单冻结的法律内容 Locale；Client API 尚无语言字段，当前恒为 en。 */
    static final String CONTENT_LOCALE = "en";

    private final Clock clock;
    private final CheckoutCommercialPort commercial;
    private final CheckoutRiskPort risk;
    private final CheckoutTaxPort tax;
    private final CheckoutDocumentPort documents;

    private final CheckoutPublicOrigin publicOrigin;
    private final CheckoutCreationService checkoutCreationService;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CommerceIdentifierGenerator identifierGenerator;
    private final CounterpartyCustomerClient counterparty;

    /** 检查商品标识唯一性及金额关系；字段约束由 HTTP 入口和公共模板触发。 */
    @Override
    protected void validate(CreateCheckoutRequest request) {
        super.validate(request);
        if (request.hasDuplicateClientItemIds()) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR);
        }
        validateAmounts(request);
        ConsumerReferencePolicy.requireOpaque(request.getConsumerReference());
    }

    /** 创建包含完整商业信息的订单和 Checkout 会话。 */
    @Override
    protected CreateCheckoutResponse processRequest(CreateCheckoutRequest request) {
        // 检查业务订单号
        checkClientOrderId(request);

        // 构建上下文
        CreateCheckoutContext context = buildCreateCheckoutContext(request);

        // 事务外初始化卖方、合同、税额和法律文件；尚不创建 Intent。
        initializeCheckout(context);

        // 带消费者引用时关联或新建 Customer；Customer 可复用，订单保存失败时保留。
        attachCustomer(context, request);

        // 保存上下文
        saveCreateCheckoutContext(context);

        // 响应处理
        return processResponse(context);
    }

    /** 默认三十分钟；显式期限按进入本次处理的时刻验证，CCT 耗时不延长期限。 */
    Instant resolveExpiresAt(CreateCheckoutRequest request, Instant requestedAt) {
        if (request.getExpiresAt() == null) {
            return requestedAt.plus(Duration.ofMinutes(30));
        }
        Instant expiresAt = request.getExpiresAt().truncatedTo(ChronoUnit.MICROS);
        Duration duration = Duration.between(requestedAt, expiresAt);
        if (duration.compareTo(Duration.ofMinutes(5)) < 0 || duration.compareTo(Duration.ofHours(24)) > 0) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR);
        }
        return expiresAt;
    }

    /** 同时验证行金额和订单汇总的精度与范围，不进行隐式舍入。 */
    void validateAmounts(CreateCheckoutRequest request) {
        try {
            var amounts = request.calculateAmounts();
            for (BigDecimal amount : List.of(amounts.merchandise(), amounts.discount(), amounts.net())) {
                CommerceDecimalRules.requireRange(amount);
                Money.of(request.getCurrency(), amount);
            }
        } catch (IllegalArgumentException | InvalidParameterException exception) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR, null, exception);
        }
    }

    /** 提前拒绝永久占用的 Client 订单号；并发竞争仍由数据库唯一索引保护。 */
    void checkClientOrderId(CreateCheckoutRequest request) {
        if (commerceOrderRepository.findOrderId(request.getClientId(), request.getClientOrderId()) != null) {
            throw new FailException(CommerceCheckoutErrorCode.CLIENT_ORDER_ID_ALREADY_EXISTS);
        }
    }

    /**
     * 所有 Owner 调用在保存前完成：商业解析按 acquiring.commercial.resolution-mode 走 commercial 或固定 Mock，Risk 经正式客户端评估，
     * tax 按 Client 声明国家给出暂估报价（revision 1，PROVISIONAL）；Document 仍按批准的固定 Mock 渲染合同。
     * <li>计税主体取自商业关系快照的 TAX_RESPONSIBLE；快照存在却缺少它时在 Risk 评估前失败关闭。没有快照（CLIENT 来源，责任方在
     * Client 一侧）时不传责任方，tax 返回 NOT_MANAGED。</li>
     * <li>BLOCKED 仍建单（technique D-02），金额为空、amountStatus=BLOCKED，消费者资料提交后重算。</li>
     */
    void initializeCheckout(CreateCheckoutContext context) {
        var order = context.getOrder();
        var input = new InitializationRequest(order.getOrderId(), order.getOrderNo(), order.getClientId(),
            order.getConsumerCountryCode(), order.getCurrency(), CONTENT_LOCALE, order.getCreateTime(),
            order.getMerchandiseAmount(), order.getDiscountAmount(), order.getNetAmount(), context.getItems());
        var facts = commercial.resolve(input);
        CheckoutInitializationContext.validateCommercial(facts);
        var parties = facts.relationship() == null ? TaxParties.unresolved(facts.businessMode())
            : TaxParties.from(facts.relationship().bindings());
        var riskId = risk.assess(input, facts);
        var quote = tax.calculate(provisionalQuote(context, facts.entityNo(), parties));
        var rendered = documents.render(input, facts.seller(), quote);
        var initialization = new CheckoutInitializationContext(input, facts, riskId, quote, rendered);
        context.setFrozen(initialization.buildFrozen(nextId(), nextId(), nextId()));
        context.setRelationshipBindings(initialization.buildRelationshipBindings(this::nextId));
        context.setTaxCalculation(initialization.buildTaxCalculation(nextId()));
        order.setEntityNo(facts.entityNo());
        order.setEntitySourceType(facts.entitySourceType());
        order.setLocalPartnerId(facts.localPartnerId());
        order.setIndustryCode(facts.industryCode());
        order.setMccCode(facts.mccCode());
        applyQuote(order, context.getTaxCalculation());
    }

    /** 建单暂估报价：revision 1、PROVISIONAL、Client 声明国家，税点为建单 UTC 日期。 */
    TaxQuoteRequest provisionalQuote(CreateCheckoutContext context, String entityNo, TaxParties parties) {
        var order = context.getOrder();
        return TaxQuoteRequest.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo())
            .revision(CheckoutInitializationContext.INITIAL_REVISION).stage(TaxCalculationStage.PROVISIONAL)
            .countryCode(order.getConsumerCountryCode()).clientId(order.getClientId()).entityNo(entityNo)
            .parties(parties).currency(order.getCurrency())
            .taxPointDate(LocalDate.ofInstant(order.getCreateTime(), ZoneOffset.UTC)).items(context.getItems()).build();
    }

    /** 订单当前报价取建单计算；订单开放供消费者提交资料，是否可支付由金额状态与支付门禁决定。 */
    void applyQuote(CommerceOrder order, OrderTaxCalculation quote) {
        order.setTaxAmount(quote.getTaxAmount());
        order.setRoundingAmount(quote.getRoundingAmount());
        order.setTotalAmount(quote.getTotalAmount());
        order.setAmountStatus(quote.amountStatus());
        order.setOrderStatus(OrderStatus.OPEN);
    }

    /**
     * 访客订单不建 Customer；有引用时在订单事务前经 counterparty 建或取有效 Customer，并把主键写入订单（软引用，只存 ID）。
     * <li>counterparty 建或取与订单保存不是原子的：订单保存失败时 Customer 保留，可被后续订单复用。</li>
     */
    void attachCustomer(CreateCheckoutContext context, CreateCheckoutRequest request) {
        if (request.getConsumerReference() == null) {
            return;
        }
        var order = context.getOrder();
        order.setCustomerId(counterparty.resolveCustomerId(order.getClientId(), request.getConsumerReference()));
    }

    /** 保存失败后事务已回滚；只有已存在同 Client 业务订单时才转换为业务冲突。 */
    void saveCreateCheckoutContext(CreateCheckoutContext context) {
        try {
            checkoutCreationService.saveCreateCheckoutContext(context);
        } catch (DuplicateKeyException exception) {
            var order = context.getOrder();
            var existing = commerceOrderRepository.findOrderId(order.getClientId(), order.getClientOrderId());
            if (existing != null && existing != order.getOrderId()) {
                throw new FailException(CommerceCheckoutErrorCode.CLIENT_ORDER_ID_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    /** 验证新建期限并一次构造保存所需的完整上下文。 */
    CreateCheckoutContext buildCreateCheckoutContext(CreateCheckoutRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var amounts = request.calculateAmounts();
        var order = CommerceOrder.builder().orderId(nextId())
            .orderNo("ord_"
                + PublicTokenGenerator.generate(Long.toString(request.getClientId()), request.getClientOrderId()))
            .clientId(request.getClientId()).clientOrderId(request.getClientOrderId())
            .creationMode(OrderCreationMode.HOSTED_CHECKOUT).consumerCountryCode(request.getConsumerCountryCode())
            .merchandiseAmount(amount(amounts.merchandise())).discountAmount(amount(amounts.discount()))
            .netAmount(amount(amounts.net())).currency(request.getCurrency()).orderStatus(OrderStatus.CREATED)
            .expiresAt(resolveExpiresAt(request, now)).version(1).createTime(now).updateTime(now).build();
        return new CreateCheckoutContext(null, List.of(), null, order, buildOrderItems(order, amounts.lines()),
            buildRefundPosition(order), buildFundsAvailability(order), buildCheckoutSession(order));
    }

    /** 按请求顺序构造商品行，税额和最终金额留待支付准备。 */
    List<OrderItem> buildOrderItems(CommerceOrder order, List<CreateCheckoutRequest.LineAmounts> lines) {
        var items = new ArrayList<OrderItem>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var item = line.item();
            items.add(OrderItem.builder().orderItemId(nextId()).orderId(order.getOrderId())
                .clientItemId(item.getClientItemId()).itemName(item.getName()).itemDescription(item.getDescription())
                .productTypeCode(item.getProductTypeCode()).taxCategoryCode(item.getTaxCategoryCode())
                .quantity(amount(new BigDecimal(item.getQuantity())))
                .unitAmount(amount(new BigDecimal(item.getUnitAmount()))).lineGrossAmount(amount(line.merchandise()))
                .lineDiscountAmount(amount(line.discount())).lineNetAmount(amount(line.net()))
                .currency(order.getCurrency()).sortNo(index).createTime(order.getCreateTime()).build());
        }
        return List.copyOf(items);
    }

    /** 未发生支付时，退款基准、预占和已退款金额均为零。 */
    OrderRefundPosition buildRefundPosition(CommerceOrder order) {
        return OrderRefundPosition.builder().orderRefundPositionId(nextId()).orderId(order.getOrderId())
            .refundableBaseAmount(amount(BigDecimal.ZERO)).reservedAmount(amount(BigDecimal.ZERO))
            .refundedAmount(amount(BigDecimal.ZERO)).currency(order.getCurrency()).version(1L)
            .createTime(order.getCreateTime()).updateTime(order.getCreateTime()).build();
    }

    /** 未产生资金事实时，只建立 NOT_APPLICABLE 状态的初始投影。 */
    OrderFundsAvailabilityProjection buildFundsAvailability(CommerceOrder order) {
        return OrderFundsAvailabilityProjection.builder().orderFundsAvailabilityProjectionId(nextId())
            .orderId(order.getOrderId()).fundsAvailabilityStatus(FundsAvailabilityStatus.NOT_APPLICABLE)
            .currency(order.getCurrency()).asOf(order.getCreateTime()).version(1L).createTime(order.getCreateTime())
            .updateTime(order.getCreateTime()).build();
    }

    /** 新会话直接处于 OPEN，Token 只生成一次并由数据库唯一索引保护。 */
    CheckoutSession buildCheckoutSession(CommerceOrder order) {
        String publicToken = CheckoutPublicToken.PREFIX
            + PublicTokenGenerator.generate(order.getConsumerCountryCode(), Long.toString(order.getClientId()));
        return CheckoutSession.builder().checkoutSessionId(nextId()).orderId(order.getOrderId())
            .publicToken(publicToken).publicOrigin(publicOrigin.value()).sessionStatus(CheckoutSessionStatus.OPEN)
            .openedAt(order.getCreateTime()).version(1L).createTime(order.getCreateTime())
            .updateTime(order.getCreateTime()).build();
    }

    /** 从同一上下文返回实际保存的结果，不重新查询数据库。 */
    CreateCheckoutResponse processResponse(CreateCheckoutContext context) {
        var order = context.getOrder();
        var session = context.getSession();
        return CreateCheckoutResponse.builder().orderNo(order.getOrderNo()).clientOrderId(order.getClientOrderId())
            .checkoutUrl(new CheckoutPublicOrigin(session.getPublicOrigin()).checkoutUrl(session.getPublicToken()))
            .currency(order.getCurrency()).merchandiseAmount(order.getMerchandiseAmount())
            .discountAmount(order.getDiscountAmount()).netAmount(order.getNetAmount()).taxAmount(order.getTaxAmount())
            .roundingAmount(order.getRoundingAmount()).totalAmount(order.getTotalAmount())
            .amountStatus(order.getAmountStatus()).taxBlockReason(context.getTaxCalculation().getBlockReasonCode())
            .expiresAt(order.getExpiresAt()).location("/api/v1/orders/" + order.getOrderNo()).build();
    }

    /** 本地标识必须是可持久化的正数。 */
    long nextId() {
        long value = identifierGenerator.nextId();
        if (value <= 0) {
            throw new ErrorException("Commerce ID 生成器返回了非正值");
        }
        return value;
    }

    /** 已验证金额按数据库八位小数精确保存，不执行舍入。 */
    BigDecimal amount(BigDecimal value) {
        return value.setScale(8, RoundingMode.UNNECESSARY);
    }
}
