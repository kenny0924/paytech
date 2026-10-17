package com.paytech.acquiring.domainservice.repository.order;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.order.*;
import com.paytech.acquiring.core.dal.mapper.checkout.BasicCheckoutMapper;
import com.paytech.acquiring.core.dal.mapper.order.OrderQueryMapper;

import lombok.AllArgsConstructor;

/** 查询本库订单事实；写入逻辑位于对应单表 Repository。 */
@Repository
@AllArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderQueryMapper orderQueryMapper;
    private final BasicCheckoutMapper basicCheckoutMapper;

    @Override
    public OrderRecord find(long clientId, String orderNo, String clientOrderId, boolean lock) {
        return orderQueryMapper.selectOrder(clientId, orderNo, clientOrderId, lock);
    }

    @Override
    public List<OrderItem> items(long clientId, long orderId) {
        return basicCheckoutMapper.selectItems(clientId, orderId);
    }

    @Override
    public OrderPaymentRecord payment(long orderId) {
        return orderQueryMapper.selectPayment(orderId);
    }

    @Override
    public OrderRefundPosition refund(long orderId) {
        return orderQueryMapper.selectRefund(orderId);
    }

    @Override
    public OrderFundsRecord funds(long orderId) {
        return orderQueryMapper.selectFunds(orderId);
    }

}
