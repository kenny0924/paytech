package com.paytech.commerce.service.processor.customer;

import com.paytech.commerce.domainservice.repository.customer.ConsumerAcceptanceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.commerce.domain.checkout.CheckoutPublicToken;
import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.commerce.domain.customer.CheckoutLegalView;
import com.paytech.commerce.domain.customer.CheckoutQueryContext;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;
import com.paytech.commerce.domainservice.port.customer.CheckoutPaymentPort;
import com.paytech.commerce.domainservice.port.customer.ConsumerProtectionPort;
import com.paytech.commerce.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.commerce.domainservice.repository.customer.ConsumerContextRepository;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.repository.order.OrderContextRepository;
import com.paytech.commerce.domainservice.repository.order.OrderItemRepository;
import com.paytech.commerce.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.commerce.integration.customer.CheckoutDocumentLinkClient;
import com.paytech.commerce.service.processor.customer.request.GetCheckoutRequest;
import com.paytech.commerce.service.processor.customer.response.GetCheckoutResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 加载与轮询入口：按需读取并重验授权，全程不创建事务、不写入或推进支付。 */
@Component
@AllArgsConstructor
public class GetCustomerCheckoutProcessor extends AbstractProcessor<GetCheckoutRequest, GetCheckoutResponse> {
    private final Clock clock;
    private final ConsumerAcceptanceRepository acceptances;
    private final CheckoutPaymentPort payment;
    private final OrderItemRepository orderItemRepository;
    private final ConsumerProtectionPort consumerProtection;
    private final OrderContextRepository orderContextRepository;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ConsumerContextRepository consumerContextRepository;
    private final CheckoutDocumentLinkClient documents;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;

    /** Token 的格式与校验码不合法时按原合同返回不存在，不发起数据库或外部查询。 */
    @Override
    protected void validate(GetCheckoutRequest request) {
        if (!CheckoutPublicToken.isValid(request.getCheckoutToken())) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
    }

    /** 读取后重验版本；事实变化时返回冲突，由调用方重新查询。 */
    @Override
    protected GetCheckoutResponse processRequest(GetCheckoutRequest request) {
        var context = buildContext(request);
        if (unchanged(request.getCheckoutToken(), context)) {
            context.validateFrozen();
            return processResponse(context, clock.instant());
        }
        throw new FailException(CommerceCheckoutErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    /** 查询本次需要的本库对象和外部只读结果后，一次构造完整上下文。 */
    CheckoutQueryContext buildContext(GetCheckoutRequest request) {
        var session = getAndCheckSession(request.getCheckoutToken());
        var order = getAndCheckOrder(session.getOrderId());
        var consumer = consumerContextRepository.findByOrderId(order.getOrderId());
        var legal = orderContextRepository.findCheckoutLegal(order.getOrderId());
        var projection = orderPaymentProjectionRepository.findByOrderId(order.getOrderId());
        var items = orderItemRepository.findByOrderId(order.getOrderId());
        var country = legal == null ? order.getConsumerCountryCode() : legal.getConsumerCountryCode();
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        order.checkExpiry(now);
        var details = consumerDetails(consumer, request.isIncludeConsumer());
        var links = documentLinks(legal, order.getExpiresAt(), now);
        var paymentStatus = paymentStatus(projection);
        return new CheckoutQueryContext(session, order, consumer, legal, projection, items, country, details, links,
            paymentStatus, acceptances.existsByOrderId(order.getOrderId()));
    }

    /** 按原 Token 读取实际会话，不拼装其他用例的订单聚合。 */
    CheckoutSession getAndCheckSession(String token) {
        var session = checkoutSessionRepository.findByPublicToken(token);
        if (session == null) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
        return session;
    }

    /** 令牌绑定的订单必须存在且未到期；先于任何个人化或支付查询。 */
    CommerceOrder getAndCheckOrder(long orderId) {
        var order = commerceOrderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
        order.checkExpiry(clock.instant());
        return order;
    }

    /** 只有显式要求回填且存在本地资料时才执行受控 Reveal。 */
    ConsumerDetails consumerDetails(ConsumerContext consumer, boolean includeConsumer) {
        if (!includeConsumer || consumer == null) {
            return null;
        }
        var details = consumerProtection
            .reveal(new ProtectedConsumer(consumer.getEmailVaultToken(), consumer.getConsumerProfileVaultToken()));
        if (details == null) {
            throw new CheckoutUpstreamProtocolException("消费者资料读取结果不完整");
        }
        return details;
    }

    /** 只访问三份原冻结文件；不完整关联在版本重验后由上下文判断。 */
    List<FileLink> documentLinks(CheckoutLegalView legal, Instant expiresAt, Instant now) {
        if (legal == null || !legal.hasContract()) {
            return List.of();
        }
        return List.of(documents.link(legal.getRenderedFileId(), legal.getRenderedContentHash(), expiresAt, now),
            documents.link(legal.getRefundPolicyFileId(), legal.getRefundPolicyContentHash(), expiresAt, now),
            documents.link(legal.getPrivacyNoticeFileId(), legal.getPrivacyNoticeContentHash(), expiresAt, now));
    }

    /** 仅依赖不可用可降级为 UNAVAILABLE；畸形支付摘要继续失败关闭。 */
    PaymentStatus paymentStatus(OrderPaymentProjection projection) {
        if (projection == null) {
            return null;
        }
        try {
            var status = payment.query(projection.getOrderId(), projection.getPaymentIntentId());
            if (status == null) {
                throw new CheckoutUpstreamProtocolException("Payment 概要缺失");
            }
            status.validate(clock.instant());
            return status;
        } catch (ServiceUnavailableException dependencyUnavailable) {
            return null;
        }
    }

    /** 末尾只读取版本与原 Token 绑定，不重新读取商品、合同或消费者详情。 */
    boolean unchanged(String token, CheckoutQueryContext context) {
        var order = context.getOrder();
        var versions = checkoutSessionRepository.findQueryVersions(token, order.getOrderId(),
            context.getSession().getCheckoutSessionId());
        if (versions == null) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
        var paymentVersion = orderPaymentProjectionRepository.findVersionByOrderId(order.getOrderId());
        order.checkExpiry(clock.instant());
        return context.matchesVersions(versions, paymentVersion);
    }

    /** 从同一上下文输出最小响应，不向 Controller 传递数据库对象或受保护引用。 */
    GetCheckoutResponse processResponse(CheckoutQueryContext context, Instant now) {
        var order = context.getOrder();
        var legal = context.getLegal();
        var amounts = new GetCheckoutResponse.Amounts(order.getMerchandiseAmount(), order.getDiscountAmount(),
            order.getNetAmount(), order.getTaxAmount(), order.getRoundingAmount(), order.getTotalAmount());
        var items = context.getItems().stream()
            .map(item -> new GetCheckoutResponse.Item(item.itemName(), item.itemDescription(), item.quantity(),
                item.unitAmount(), item.lineDiscountAmount(), item.lineNetAmount()))
            .toList();
        var seller = legal == null ? null
            : new Seller(legal.getPublicLegalName(), legal.getPublicRegistrationNumber(),
                legal.getPublicTaxRegistrationNumber(), legal.getPublicAddressText(), legal.getPublicSupportEmail(),
                legal.getPublicSupportPhone());
        var actions = context.allowedActions(now);
        return new GetCheckoutResponse(order.getOrderNo(), order.getOrderStatus(),
            context.getSession().getSessionStatus(), context.isReady(), order.getCurrency(), context.getCountry(),
            legal == null ? "en" : legal.getLocale(), order.getExpiresAt(), amounts, items,
            context.getConsumer() != null, context.getDetails(), seller,
            legal == null ? null : legal.getConsumerContractId(), context.getLinks(),
            context.getPaymentProjection() != null, context.getPayment(), actions, context.pollAfterMs(actions));
    }
}
