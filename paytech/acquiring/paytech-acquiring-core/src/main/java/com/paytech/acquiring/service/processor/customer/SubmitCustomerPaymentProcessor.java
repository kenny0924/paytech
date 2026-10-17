package com.paytech.commerce.service.processor.customer;

import static com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.customer.CheckoutNetworkEvidence;
import com.paytech.commerce.core.dal.dataobject.customer.ConsumerAcceptance;
import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContract;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.domain.checkout.CheckoutPublicOrigin;
import com.paytech.commerce.domain.checkout.CheckoutPublicToken;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.CardChoice;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.Payer;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentRequest;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.SubmissionReceipt;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.IntentRequest;
import com.paytech.commerce.domain.customer.SubmitCustomerPaymentContext;
import com.paytech.commerce.domain.enums.CheckoutEvidenceScene;
import com.paytech.commerce.domain.enums.ConsumerAcceptanceType;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.commerce.domainservice.port.customer.CheckoutCardPaymentPort;
import com.paytech.commerce.integration.payment.PaymentCheckoutClient;
import com.paytech.commerce.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.commerce.domainservice.repository.customer.CheckoutNetworkEvidenceRepository;
import com.paytech.commerce.domainservice.repository.customer.ConsumerAcceptanceRepository;
import com.paytech.commerce.domainservice.repository.customer.ConsumerContextRepository;
import com.paytech.commerce.domainservice.repository.customer.ConsumerContractRepository;
import com.paytech.commerce.domainservice.repository.customer.CustomerRepository;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.repository.order.OrderContextRepository;
import com.paytech.commerce.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.commerce.domainservice.service.customer.CheckoutSubmissionService;
import com.paytech.commerce.service.processor.customer.request.SubmitCustomerPaymentRequest;
import com.paytech.commerce.service.processor.customer.response.CustomerPaymentResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/** 支付提交入口：查询与校验、构建上下文、本地受理、事务外 Payment 调用和响应。 */
@Component
@AllArgsConstructor
public class SubmitCustomerPaymentProcessor
    extends AbstractLockProcessor<SubmitCustomerPaymentRequest, CustomerPaymentResponse> {
    private final Clock clock;
    private final PaymentCheckoutClient intents;
    private final OrderContextRepository contexts;
    private final CheckoutCardPaymentPort payment;
    private final CommerceOrderRepository orderRepository;
    private final CheckoutSessionRepository sessionRepository;
    private final CheckoutSubmissionService submissions;
    private final ConsumerContextRepository consumerRepository;
    private final ConsumerContractRepository contractRepository;
    private final CommerceIdentifierGenerator identifiers;
    private final ConsumerAcceptanceRepository acceptanceRepository;
    private final OrderPaymentProjectionRepository paymentRepository;
    private final CheckoutNetworkEvidenceRepository evidenceRepository;
    private final CardSavingEligibility cardSavingEligibility;
    private final CustomerRepository customerRepository;

    /** 非 HTTP 调用同样执行字段约束；不访问数据库，不复制模板的事务检查。 */
    @Override
    protected void validate(SubmitCustomerPaymentRequest request) {
        if (!CheckoutPublicToken.isValid(request.getCheckoutToken())) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        request.getEvidence().validate();
        if ((request.getCard() == null) == (request.getStoredCardToken() == null)
            || (request.isSaveCard() && request.getStoredCardToken() != null)) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR);
        }
    }

    /** 锁前仅解析不可变订单身份；不使用公开 Token 作 Redis Key，锁内再读取业务状态。 */
    @Override
    protected LockCommand lockCommand(SubmitCustomerPaymentRequest request) {
        var orderId = sessionRepository.findOrderIdByPublicToken(request.getCheckoutToken());
        if (orderId == null) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        return LockCommand.tryLock("order:" + orderId);
    }

    /** 每次请求按当前业务事实受理，提交本地事务后调用 Payment。 */
    @Override
    protected CustomerPaymentResponse processWithinLock(SubmitCustomerPaymentRequest request) {
        var session = getAndCheckSession(request.getCheckoutToken());
        var order = getAndCheckOrder(session.getOrderId());
        checkNewSubmission(order, session);
        var choice = buildCardChoice(request, order);
        var contract = getAndCheckContract(order, request.getConsumerContractId());
        var projection = getAndCheckPayment(order);
        var context = buildContext(request, order, session, contract, projection);
        // 先提交接受事实，作为 Intent 可能已创建的持久化边界。
        submissions.saveSubmissionContext(context);
        // 事务外创建后，在一个本地事务中保存关联并确认支付资格。
        if (context.getPayment() == null) {
            createIntent(context);
            submissions.bindPayment(context);
        }
        return executePayment(context, choice, buildPayer(request, context));
    }

    /** 保存卡或使用已存卡时必须有存卡资格；Customer 只在这两种情况下交给 Payment。 */
    CardChoice buildCardChoice(SubmitCustomerPaymentRequest request, CommerceOrder order) {
        if (!request.isSaveCard() && request.getStoredCardToken() == null) {
            return new CardChoice(request.getCard(), null, null, null, false);
        }
        var customer = cardSavingEligibility.eligibleCustomer(order);
        if (customer == null) {
            throw new FailException(STORED_CARD_NOT_AVAILABLE);
        }
        return new CardChoice(request.getCard(), request.getStoredCardToken(), request.getStoredCardCvv(),
            customer.getCustomerId(), request.isSaveCard());
    }

    /** 付款人引用不传 Client 原始引用：有 Customer 时用公开 Customer 编号，访客用订单号；回跳到本 Checkout 页面。 */
    Payer buildPayer(SubmitCustomerPaymentRequest request, SubmitCustomerPaymentContext context) {
        var order = context.getOrder();
        var customer = order.getCustomerId() == null ? null : customerRepository.findById(order.getCustomerId());
        var session = context.getSession();
        var evidence = request.getEvidence();
        String userAgent = evidence.userAgent() == null || evidence.userAgent().isBlank() ? null : evidence.userAgent();
        return new Payer(customer == null ? order.getOrderNo() : customer.getCustomerNo(),
            evidence.clientIpAddress(), userAgent,
            new CheckoutPublicOrigin(session.getPublicOrigin()).checkoutUrl(session.getPublicToken()));
    }

    /** Token 只定位原会话，不加载订单关联聚合。 */
    CheckoutSession getAndCheckSession(String token) {
        var session = sessionRepository.findByPublicToken(token);
        if (session == null) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        return session;
    }

    /** 访问期限来自原订单，不因再次提交延长。 */
    CommerceOrder getAndCheckOrder(long orderId) {
        var order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        order.checkExpiry(clock.instant());
        return order;
    }

    /** 当前请求必须确认原订单准确冻结的合同。 */
    ConsumerContract getAndCheckContract(CommerceOrder order, long contractId) {
        var contract = contractRepository.findByOrderId(order.getOrderId());
        if (contract == null) {
            throw new FailException(CHECKOUT_NOT_READY);
        }
        if (contract.getConsumerContractId() != contractId) {
            throw new FailException(LEGAL_DOCUMENT_CHANGED);
        }
        return contract;
    }

    /** 按需读取已有 Intent；尚无关联允许首次创建。 */
    OrderPaymentProjection getAndCheckPayment(CommerceOrder order) {
        var projection = paymentRepository.findByOrderId(order.getOrderId());
        if (projection != null && projection.hasSuccessfulPayment()) {
            throw new FailException(CHECKOUT_CLOSED);
        }
        return projection;
    }

    /** 每次提交检查开放状态和资料存在性；终态不能创建新的支付。 */
    void checkNewSubmission(CommerceOrder order, CheckoutSession session) {
        if (order.getTerminationRequestedAt() != null || order.getPaidAt() != null
            || !order.statusIn(OrderStatus.CREATED, OrderStatus.OPEN) || !session.allowsPayment()) {
            throw new FailException(CHECKOUT_CLOSED);
        }
        if (!order.allowsPayment() || !consumerRepository.existsByOrderId(order.getOrderId())) {
            throw new FailException(CHECKOUT_NOT_READY);
        }
    }

    /** 一次构造待保存上下文；已有接受按原证据复用，不把卡输入交给本地 Service。 */
    SubmitCustomerPaymentContext buildContext(SubmitCustomerPaymentRequest request, CommerceOrder order,
        CheckoutSession session, ConsumerContract contract, OrderPaymentProjection projection) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long submissionId = identifiers.nextId();
        var acceptances = acceptanceRepository.findByContractId(contract.getConsumerContractId());
        boolean newAcceptances = acceptances.isEmpty();
        CheckoutNetworkEvidence evidence;
        if (newAcceptances) {
            evidence = buildEvidence(request, order, session, now);
            acceptances = buildAcceptances(contract, evidence, now);
        } else {
            evidence = evidenceRepository.findBindingById(acceptances.getFirst().getNetworkEvidenceId());
        }
        var context = new SubmitCustomerPaymentContext(order, session, contract, projection, submissionId,
            newAcceptances, evidence, acceptances);
        context.validatedAcceptances();
        return context;
    }

    /** 新法律接受只保留受信连接原值，不持久化卡信息、公开 Token 或客户端自报证据。 */
    CheckoutNetworkEvidence buildEvidence(SubmitCustomerPaymentRequest request, CommerceOrder order,
        CheckoutSession session, Instant now) {
        var source = request.getEvidence();
        return CheckoutNetworkEvidence.builder().networkEvidenceId(identifiers.nextId()).orderId(order.getOrderId())
            .checkoutSessionId(session.getCheckoutSessionId()).evidenceScene(CheckoutEvidenceScene.LEGAL_ACCEPTANCE)
            .requestId(request.getRequestId()).clientIpAddress(source.clientIpAddress()).userAgent(source.userAgent())
            .trustedProxyChain(source.trustedProxyChain()).occurredAt(now).createTime(now).build();
    }

    /** 首次点击支付创建准确的三份接受，同一合同后续提交复用这些身份和时刻。 */
    List<ConsumerAcceptance> buildAcceptances(ConsumerContract contract, CheckoutNetworkEvidence evidence,
        Instant now) {
        return Arrays.stream(ConsumerAcceptanceType.values())
            .map(type -> ConsumerAcceptance.builder().consumerAcceptanceId(identifiers.nextId())
                .consumerContractId(contract.getConsumerContractId()).networkEvidenceId(evidence.getNetworkEvidenceId())
                .acceptanceType(type).acceptedAt(now).createTime(now).build())
            .toList();
    }

    /** 首次提交才创建 Intent；响应未知时保留接受事实，后续以同一订单恢复。 */
    void createIntent(SubmitCustomerPaymentContext context) {
        var order = context.getOrder();
        var input = IntentRequest.from(order, contexts.findFrozen(order.getOrderId()));
        var result = intents.createIntent(input);
        result.checkPayable();
        var projection = OrderPaymentProjection.created(identifiers.nextId(), order, result,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        context.setPayment(projection);
    }

    /** 复用已提交的上下文和回写版本；到期判断只比较原期限，不重复查库。 */
    CustomerPaymentResponse executePayment(SubmitCustomerPaymentContext context, CardChoice card, Payer payer) {
        var order = context.getOrder();
        order.checkExpiry(clock.instant());
        var receipt = context.submission();
        var instrument = intents.applyCardInstrument(order.getClientId(), receipt.reference(), card);
        var now = clock.instant();
        order.checkExpiry(now);
        instrument.checkUsable(now, order.getExpiresAt());
        var request = new PaymentRequest(receipt, order.getVersion(), order.getClientId(), instrument, payer);
        return processResponse(receipt, invokePayment(receipt, request));
    }

    /** 提交响应未知时返回等待结果，由公开查询接口读取 Payment 当前事实。 */
    PaymentResult invokePayment(SubmissionReceipt receipt, PaymentRequest request) {
        PaymentResult result;
        try {
            result = payment.submit(request);
        } catch (ServiceUnavailableException responseUnknown) {
            return null;
        }
        if (result != null) {
            result.validate(receipt.reference(), clock.instant());
        }
        return result;
    }

    /** 从原受理事实和当前 Payment 结果构造内部响应，不重查展示聚合。 */
    CustomerPaymentResponse processResponse(SubmissionReceipt receipt, PaymentResult result) {
        return new CustomerPaymentResponse(receipt.reference(), receipt.expiresAt(), result);
    }
}
