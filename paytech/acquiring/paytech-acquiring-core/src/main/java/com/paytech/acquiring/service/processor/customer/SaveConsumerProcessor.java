package com.paytech.acquiring.service.processor.customer;

import com.paytech.acquiring.domainservice.repository.customer.ConsumerAcceptanceRepository;
import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.domain.checkout.CheckoutPublicToken;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.acquiring.domain.customer.SaveConsumerContext;
import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.acquiring.domainservice.port.customer.ConsumerProtectionPort;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerContextRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.service.customer.CustomerCheckoutService;
import com.paytech.acquiring.service.processor.customer.request.SaveConsumerRequest;
import com.paytech.acquiring.service.processor.customer.response.SaveConsumerResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import jakarta.validation.Validator;
import lombok.AllArgsConstructor;

/** 保存完整消费者资料；外部保护在事务前，订单版本与资料在本地原子提交。 */
@Component
@AllArgsConstructor
public class SaveConsumerProcessor extends AbstractProcessor<SaveConsumerRequest, SaveConsumerResponse> {
    private final Clock clock;
    private final ConsumerAcceptanceRepository acceptances;
    private final Validator validator;
    private final ConsumerProtectionPort protection;
    private final CommerceOrderRepository orderRepository;
    private final CustomerCheckoutService checkout;
    private final CheckoutSessionRepository sessionRepository;
    private final ConsumerContextRepository consumerRepository;
    private final CommerceIdentifierGenerator identifiers;

    /** 保留无效 Token 的不存在语义，不在基础校验阶段访问数据库或 Vault。 */
    @Override
    protected void validate(SaveConsumerRequest request) {
        if (!CheckoutPublicToken.isValid(request.getCheckoutToken())) {
            throw new FailException(CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND);
        }
    }

    /** 每次请求按当前订单状态验证并保存完整资料。 */
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
        checkout.saveConsumerContext(context);
        return processResponse(context.getOrder());
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
        return new SaveConsumerContext(order, session, consumer, details, values);
    }

    /** 只返回保存状态，不重新加载资料或回显原值。 */
    SaveConsumerResponse processResponse(CommerceOrder order) {
        return new SaveConsumerResponse(order.getOrderNo(), "COMPLETE");
    }
}
