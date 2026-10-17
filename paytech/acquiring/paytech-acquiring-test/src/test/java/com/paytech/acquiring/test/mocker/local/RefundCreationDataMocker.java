package com.paytech.acquiring.test.mocker.local;

import java.math.BigDecimal;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.api.client.v1.refund.request.CreateRefundRequestBody;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.acquiring.domain.enums.RefundRequestOrigin;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.domain.refund.RefundCreationContext;
import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.dal.RefundCreationTestMapper;

import lombok.AllArgsConstructor;

/** 退款前置事实直接落库；资源由订单 Mocker 统一登记和 Clear。 */
@TestComponent
@AllArgsConstructor
public class RefundCreationDataMocker {
    private final RefundCreationTestMapper mapper;
    private final CommerceIdentifierGenerator identifiers;

    /** 构造未执行的数据库对象；供原退款场景与公开 Service 原子性测试使用。 */
    public RefundRequest refund(Fixture fixture, CreateRefundRequestBody request) {
        return template(fixture).clientRefundId(request.getClientRefundId())
            .refundAmount(new BigDecimal(request.getAmount())).reasonCode(request.getReasonCode())
            .reasonSummary(request.getReasonSummary()).build();
    }

    /** 查询及创建测试共用数据库对象 Builder，不必为查询前置数据构造创建 API 请求。 */
    public RefundRequest.RefundRequestBuilder template(Fixture fixture) {
        var order = fixture.getOrder();
        long id = identifiers.nextId();
        return RefundRequest.builder().refundRequestId(id).refundRequestNo("rfd_fixture_" + id)
            .orderId(order.getOrderId()).clientId(order.getClientId()).requestOrigin(RefundRequestOrigin.CLIENT)
            .clientRefundId("refund-" + id).refundAmount(new BigDecimal("10.00"))
            .currency(order.getCurrency()).reasonCode("REQUESTED_BY_CUSTOMER")
            .refundStatus(RefundStatus.PENDING).version(1).createTime(order.getUpdateTime())
            .updateTime(order.getUpdateTime());
    }

    /** 原退款由 Fixture 插入，不为 Arrange 调用退款入口或伪造 Payment 成功。 */
    public void mock(RefundRequest refund) {
        mapper.insertRefund(refund);
    }

    /** 直接模拟翻页期间收到的状态投影更新，不通过提交退款或外部成功替身准备数据。 */
    public void updateState(RefundRequest refund) {
        if (mapper.updateState(refund) != 1) {
            throw new IllegalStateException("前置退款不存在");
        }
    }

    /** 插入待恢复事件作为查询只读断言的前置，不唤醒 Dispatcher。 */
    public void mockPendingTask(RefundRequest refund) {
        mapper.insertTask(identifiers.nextId(), refund);
    }

    /** 使用已知的 Fixture 版本构造原子写入口参数，不查询或调用生产业务。 */
    public RefundCreationContext context(Fixture fixture, CreateRefundRequestBody request) {
        var payment = new OrderPaymentProjection();
        long id = fixture.getOrder().getOrderId();
        payment.setOrderPaymentProjectionId(id);
        payment.setOrderId(id);
        payment.setPaymentIntentId(id);
        payment.setIntentStatus(fixture.getPayment().getIntentStatus());
        payment.setSuccessfulAttemptId(fixture.getPayment().getSuccessfulAttemptId());
        payment.setCapturedAmount(fixture.getPayment().getCapturedAmount());
        payment.setCurrency(fixture.getPayment().getCurrency());
        payment.setVersion(11);
        return new RefundCreationContext(fixture.getOrder(), payment, fixture.getRefund(), refund(fixture, request));
    }

    /** 模拟 Context 加载后发生另一笔本地写入，不改内部 Repository 返回值。 */
    public void advancePositionVersion(long orderId) {
        if (mapper.advancePositionVersion(orderId) != 1) {
            throw new IllegalStateException("前置订单没有退款金额位置");
        }
    }
}
