package com.paytech.commerce.domainservice.repository.order;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.core.dal.mapper.order.CommerceOrderMapper;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** CommerceOrder 的 MyBatis 持久化。 */
@Repository
@AllArgsConstructor
public class CommerceOrderRepositoryImpl implements CommerceOrderRepository {
    private final Clock clock;
    private final CommerceOrderMapper commerceOrderMapper;

    /** 按已授权的订单身份读取本表，不装配 Session 或旧创建快照。 */
    @Override
    public CommerceOrder findByOrderId(long orderId) {
        return commerceOrderMapper.findByOrderId(orderId);
    }

    /** SQL 成功后同步原订单版本；任何后续失败均由调用方丢弃本次 Context。 */
    @Override
    public void recordConsumerChange(CommerceOrder order, CheckoutSession session, Instant now) {
        if (commerceOrderMapper.recordConsumerChange(order, session, now) != 1) {
            throw new ConcurrentRequestException("订单或会话已改变，请重新保存消费者资料");
        }
        order.setVersion(order.getVersion() + 1);
        order.setUpdateTime(now);
    }

    /** 条件写入同时取得实际订单行锁，后续接受保存失败由 Service 事务回滚。 */
    @Override
    public void recordPaymentSubmission(CommerceOrder order, CheckoutSession session, long contractId,
        OrderPaymentProjection payment, Instant now) {
        if (commerceOrderMapper.recordPaymentSubmission(order, session, contractId, payment, now) != 1) {
            throw new ConcurrentRequestException("订单支付资格已改变，请重新查询");
        }
        order.setVersion(order.getVersion() + 1);
        order.setUpdateTime(now);
    }

    /** 按 Client 限定订单归属，只读取订单表。 */
    @Override
    public CommerceOrder findByOrderNo(long clientId, String orderNo) {
        return commerceOrderMapper.findByOrderNo(clientId, orderNo);
    }

    /** 只读取已归属当前 Client 的订单主键，不加载订单其余业务事实。 */
    @Override
    public Long findOrderIdByOrderNo(long clientId, String orderNo) {
        return commerceOrderMapper.findOrderIdByOrderNo(clientId, orderNo);
    }

    /** 只读取归属当前 Client 的公开订单号，不加载会话、支付或金额数据。 */
    @Override
    public String findOrderNoByOrderId(long clientId, long orderId) {
        return commerceOrderMapper.findOrderNoByOrderId(clientId, orderId);
    }

    /** 查询 Client 内业务订单号的永久占用。 */
    @Override
    public Long findOrderId(long clientId, String clientOrderId) {
        return commerceOrderMapper.findOrderId(clientId, clientOrderId);
    }

    /** 插入后核对行数，失败由调用方事务整体回滚。 */
    @Override
    public void insert(CommerceOrder value) {
        if (commerceOrderMapper.insert(value, clock.instant().truncatedTo(ChronoUnit.MICROS)) != 1) {
            throw new FailException(CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE);
        }
    }

    /** SQL 行数确认后同步事实；CAS 失败由调用方回滚，不继续调用 Payment。 */
    @Override
    public void requestTermination(CommerceOrder order, Instant now) {
        if (commerceOrderMapper.requestTermination(order, now) != 1) {
            throw new ConcurrentRequestException("订单状态已改变");
        }
        order.setTerminationRequestedAt(now);
        order.setVersion(order.getVersion() + 1);
        order.setUpdateTime(now);
    }

    /** 本表取消成功后同步状态和版本；Session 更新失败时外层事务一起回滚。 */
    @Override
    public void cancel(CommerceOrder order, OrderPaymentProjection payment, boolean localOnly, Instant now) {
        if (commerceOrderMapper.cancel(order, payment, localOnly, now) != 1) {
            throw new ConcurrentRequestException("取消时订单或支付事实已改变");
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setVersion(order.getVersion() + 1);
        order.setUpdateTime(now);
    }

    /** 行数确认后同步原订单；失败由调用方事务整体回滚。 */
    @Override
    public void markPaid(CommerceOrder order, Instant paidAt, Instant now) {
        if (commerceOrderMapper.markPaid(order, paidAt, now) != 1) {
            throw new ConcurrentRequestException("支付成功时订单已改变");
        }
        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);
        order.setVersion(order.getVersion() + 1);
        order.setUpdateTime(now);
    }
}
