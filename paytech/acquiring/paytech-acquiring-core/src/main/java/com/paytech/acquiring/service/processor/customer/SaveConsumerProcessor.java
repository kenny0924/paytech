package com.paytech.acquiring.service.processor.customer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.checkout.CheckoutPublicToken;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.InitializationRequest;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.RenderedContract;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.RoleBindingFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Seller;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxCalculation;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxParties;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxQuoteRequest;
import com.paytech.acquiring.domain.customer.OrderTaxQuotes;
import com.paytech.acquiring.domain.customer.SaveConsumerContext;
import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.acquiring.domainservice.port.customer.CheckoutDocumentPort;
import com.paytech.acquiring.domainservice.port.customer.CheckoutTaxPort;
import com.paytech.acquiring.domainservice.port.customer.ConsumerProtectionPort;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerAcceptanceRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerContextRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderContextRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderItemRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderRelationshipBindingRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderTaxCalculationRepository;
import com.paytech.acquiring.domainservice.service.customer.CustomerCheckoutService;
import com.paytech.acquiring.service.processor.customer.request.SaveConsumerRequest;
import com.paytech.acquiring.service.processor.customer.response.SaveConsumerResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.tax.api.enums.TaxCalculationStage;

import jakarta.validation.Validator;
import lombok.AllArgsConstructor;

/**
 * 保存完整消费者资料并按账单国家重算税额（technique S-04、S-05）
 * <li>事务外：资料保护、tax FINAL 计算（最新 revision + 1，账单国家）、报价金额变化时重新渲染合同。</li>
 * <li>本地事务：订单版本与当前报价、消费者资料、新计算记录与绑定该计算的新合同一次提交；已有消费者接受时整体拒绝。</li>
 * <li>税务服务上线前的历史订单没有计算记录，不重算，按原合同支付（AC-12）。</li>
 */
@Component
@AllArgsConstructor
public class SaveConsumerProcessor extends AbstractProcessor<SaveConsumerRequest, SaveConsumerResponse> {
    private final Clock clock;
    private final CheckoutTaxPort tax;
    private final Validator validator;
    private final CheckoutDocumentPort documents;
    private final OrderItemRepository itemRepository;
    private final ConsumerProtectionPort protection;
    private final CommerceOrderRepository orderRepository;
    private final OrderContextRepository contextRepository;
    private final CustomerCheckoutService checkout;
    private final CheckoutSessionRepository sessionRepository;
    private final ConsumerAcceptanceRepository acceptances;
    private final ConsumerContextRepository consumerRepository;
    private final CommerceIdentifierGenerator identifiers;
    private final OrderTaxCalculationRepository taxCalculationRepository;
    private final OrderRelationshipBindingRepository bindingRepository;

    /** 保留无效 Token 的不存在语义，不在基础校验阶段访问数据库或 Vault。 */
    @Override
    protected void validate(SaveConsumerRequest request) {
        if (!CheckoutPublicToken.isValid(request.getCheckoutToken())) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
    }

    @Override
    protected SaveConsumerResponse processRequest(SaveConsumerRequest request) {
        var details = normalize(request);
        var session = getAndCheckSession(request.getCheckoutToken());
        var order = getAndCheckOrder(session.getOrderId());
        session.checkConsumerEditable();
        order.checkConsumerEditable();
        if (acceptances.existsByOrderId(order.getOrderId())) {
            throw new FailException(CustomerCheckoutErrorCode.CONSUMER_DETAILS_LOCKED);
        }
        var values = protect(details);
        var context = buildContext(order, session, details, values);

        // 事务外按账单国家重算，报价变化时重新渲染合同；历史订单不重算。
        requote(context);

        // 保存上下文
        checkout.saveConsumerContext(context);
        return processResponse(context);
    }

    /** 规范化后再执行邮箱约束，不把原值放入异常消息。 */
    ConsumerDetails normalize(SaveConsumerRequest request) {
        var details = request.normalizeDetails();
        if (!validator.validate(details).isEmpty()) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR);
        }
        return details;
    }

    /** 原 Token 必须对应实际会话，不读取其他用例的聚合。 */
    CheckoutSession getAndCheckSession(String token) {
        var session = sessionRepository.findByPublicToken(token);
        if (session == null) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
        return session;
    }

    /** 资料保护之前先完成订单存在与访问期限校验。 */
    CommerceOrder getAndCheckOrder(long orderId) {
        var order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
        order.checkExpiry(clock.instant());
        return order;
    }

    /** Protect 是事务外调用；本地只保存保护引用，不生成伪造 Token。 */
    ProtectedConsumer protect(ConsumerDetails details) {
        var values = protection.protect(details);
        if (values == null) {
            throw new CheckoutUpstreamProtocolException("消费者保护结果缺失");
        }
        values.validate();
        return values;
    }

    /** 保留原资料的身份和版本；首次保存才预分配主键，不预先修改已持久化对象。 */
    SaveConsumerContext buildContext(CommerceOrder order, CheckoutSession session, ConsumerDetails details,
        ProtectedConsumer values) {
        var consumer = consumerRepository.findByOrderId(order.getOrderId());
        if (consumer == null) {
            consumer = new ConsumerContext();
            consumer.setConsumerContextId(identifiers.nextId());
            consumer.setOrderId(order.getOrderId());
        }
        return new SaveConsumerContext(order, session, consumer, details, values, null, null);
    }

    /**
     * 按账单国家取得最终计算并准备绑定它的合同
     * <li>同一 revision 的重复提交由 tax 返回原计算；并发提交以不同内容争用同一 revision 时 tax 返回冲突，转为 409。</li>
     */
    void requote(SaveConsumerContext context) {
        var order = context.getOrder();
        var current = taxCalculationRepository.findLatestByOrderId(order.getOrderId());
        if (current == null) {
            return;
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var items = itemRepository.findByOrderId(order.getOrderId());
        var country = context.getDetails().billingAddress().countryCode();
        int revision = current.getRevision() + 1;
        var calculation = tax.calculate(finalQuote(order, items, parties(order, current), revision, country, now));
        OrderTaxQuotes.validate(calculation, order.getCurrency(), order.getNetAmount(), items);
        var quote = OrderTaxQuotes.record(identifiers.nextId(), order.getOrderId(), revision, country, calculation,
            order.getCurrency(), now);
        context.setQuote(quote);
        context.setContract(buildContract(order, items, current, quote, calculation, now));
    }

    /**
     * 计税主体：取订单冻结的商业关系快照
     * <li>没有快照（CLIENT 来源）时本库没有合同业务模式，按 ID 从 tax 读取当前计算冻结的主体（责任方为空，结果恒为 NOT_MANAGED）。</li>
     */
    TaxParties parties(CommerceOrder order, OrderTaxCalculation current) {
        var bindings = bindingRepository.findByOrderId(order.getOrderId()).stream()
            .map(binding -> new RoleBindingFacts(binding.getRoleCode(), binding.getSubjectType(),
                binding.getSubjectId(), binding.getPartyId()))
            .toList();
        if (bindings.isEmpty()) {
            return tax.frozenParties(current.getTaxCalculationId());
        }
        return TaxParties.from(bindings);
    }

    /** 最终报价：账单国家、FINAL，税点为本次 UTC 日期。 */
    TaxQuoteRequest finalQuote(CommerceOrder order, List<OrderItem> items, TaxParties parties, int revision,
        String country, Instant now) {
        return TaxQuoteRequest.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo()).revision(revision)
            .stage(TaxCalculationStage.FINAL).countryCode(country).clientId(order.getClientId())
            .entityNo(order.getEntityNo()).parties(parties).currency(order.getCurrency())
            .taxPointDate(LocalDate.ofInstant(now, ZoneOffset.UTC)).items(items).build();
    }

    /**
     * 新合同行绑定新计算
     * <li>报价金额或阻断状态变化时按新计算重新渲染（S-05）；不变时沿用当前合同文件，只换绑定的计算。</li>
     */
    FrozenCheckoutRecord buildContract(CommerceOrder order, List<OrderItem> items, OrderTaxCalculation current,
        OrderTaxCalculation quote, TaxCalculation calculation, Instant now) {
        var frozen = contextRepository.findFrozen(order.getOrderId());
        if (frozen == null) {
            throw new CheckoutUpstreamProtocolException("订单缺少冻结的法律与合同事实");
        }
        RenderedContract rendered = new RenderedContract(frozen.renderedFileId(), frozen.renderedContentHash());
        if (OrderTaxQuotes.amountsChanged(current, quote)) {
            rendered = documents.render(renderInput(order, items, frozen, now), seller(frozen), calculation);
        }
        return FrozenCheckoutRecord.builder().consumerContractId(identifiers.nextId()).orderId(order.getOrderId())
            .orderLegalId(frozen.orderLegalId()).contractTaxCalculationId(calculation.calculationId())
            .renderedFileId(rendered.fileId()).renderedContentHash(rendered.contentHash()).locale(frozen.locale())
            .presentedAt(now).build();
    }

    /** 合同渲染输入：原订单商品与金额、冻结的计税国家与 Locale。 */
    InitializationRequest renderInput(CommerceOrder order, List<OrderItem> items, FrozenCheckoutRecord frozen,
        Instant now) {
        return new InitializationRequest(order.getOrderId(), order.getOrderNo(), order.getClientId(),
            frozen.consumerCountryCode(), order.getCurrency(), frozen.locale(), now, order.getMerchandiseAmount(),
            order.getDiscountAmount(), order.getNetAmount(), items);
    }

    /** 冻结的卖方公开披露。 */
    Seller seller(FrozenCheckoutRecord frozen) {
        return new Seller(frozen.publicLegalName(), frozen.publicRegistrationNumber(),
            frozen.publicTaxRegistrationNumber(), frozen.publicAddressText(), frozen.publicSupportEmail(),
            frozen.publicSupportPhone());
    }

    /** 从本次 Context 返回保存状态与当前报价，不重新加载资料或回显原值。 */
    SaveConsumerResponse processResponse(SaveConsumerContext context) {
        var order = context.getOrder();
        var quote = context.getQuote();
        return new SaveConsumerResponse(order.getOrderNo(), "COMPLETE", order.getAmountStatus(),
            quote == null ? null : quote.getBlockReasonCode(), order.getCurrency(), order.getMerchandiseAmount(),
            order.getDiscountAmount(), order.getNetAmount(), order.getTaxAmount(), order.getRoundingAmount(),
            order.getTotalAmount());
    }
}
