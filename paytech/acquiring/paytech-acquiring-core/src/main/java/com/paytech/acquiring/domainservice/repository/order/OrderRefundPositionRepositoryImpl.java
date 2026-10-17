package com.paytech.commerce.domainservice.repository.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.commerce.core.dal.mapper.order.OrderRefundPositionMapper;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 退款位置的 MyBatis 持久化；只有 SQL 成功才同步原对象。 */
@Repository
@AllArgsConstructor
public class OrderRefundPositionRepositoryImpl implements OrderRefundPositionRepository {
    private final OrderRefundPositionMapper mapper;

    /** 按订单查询额度记录。 */
    @Override
    public OrderRefundPosition findByOrderId(long orderId) {
        return mapper.findByOrderId(orderId);
    }

    /** 参与调用方本地事务的额度锁定读取。 */
    @Override
    public OrderRefundPosition lockByOrderId(long orderId) {
        return mapper.lockByOrderId(orderId);
    }

    /** 插入后核对行数，失败由调用方事务整体回滚。 */
    @Override
    public void insert(OrderRefundPosition value) {
        if (mapper.insert(value) != 1) {
            throw new ErrorException("插入 OrderRefundPosition 受影响行数异常");
        }
    }

    /** 版本、资格或剩余额度已变化时整体回滚，不重读版本后盲目重试。 */
    @Override
    public void reserve(OrderRefundPosition position, CommerceOrder order, OrderPaymentProjection payment,
        BigDecimal amount, Instant now) {
        if (mapper.reserve(position, order, payment, amount, now) != 1) {
            throw new ConcurrentRequestException("退款资格或可退款金额已改变");
        }
        position.setReservedAmount(position.getReservedAmount().add(amount));
        position.setVersion(position.getVersion() + 1);
        position.setUpdateTime(now);
    }

    /** 成功退款累计已退金额，失败或取消只释放预占。 */
    @Override
    public void settle(OrderRefundPosition position, BigDecimal amount, boolean success, Instant now) {
        if (mapper.settle(position, amount, success, now) != 1) {
            throw new ConcurrentRequestException("退款金额位置已改变");
        }
        position.setReservedAmount(position.getReservedAmount().subtract(amount));
        if (success) {
            position.setRefundedAmount(position.getRefundedAmount().add(amount));
        }
        position.setVersion(position.getVersion() + 1);
        position.setUpdateTime(now);
    }

    /** 行数确认后同步基数与版本；失败由调用方事务整体回滚。 */
    @Override
    public void establishBase(OrderRefundPosition position, BigDecimal amount, Instant now) {
        if (mapper.establishBase(position, amount, now) != 1) {
            throw new ConcurrentRequestException("支付成功时退款金额位置已改变");
        }
        position.setRefundableBaseAmount(amount);
        position.setVersion(position.getVersion() + 1);
        position.setUpdateTime(now);
    }
}
