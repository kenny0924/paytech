package com.paytech.commerce.domainservice.repository.order;

import com.paytech.foundation.common.error.ErrorException;
import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.core.dal.mapper.order.OrderPaymentProjectionMapper;

import lombok.AllArgsConstructor;

/** 维护本库支付投影，不访问 Payment 服务。 */
@Repository
@AllArgsConstructor
public class OrderPaymentProjectionRepositoryImpl implements OrderPaymentProjectionRepository {
    private final OrderPaymentProjectionMapper orderPaymentProjectionMapper;

    /** 不重复加载支付事实，仅取得重验版本。 */
    @Override
    public Long findVersionByOrderId(long orderId) {
        return orderPaymentProjectionMapper.findVersionByOrderId(orderId);
    }

    /** 按订单读取当前支付投影。 */
    @Override
    public OrderPaymentProjection findByOrderId(long orderId) {
        return orderPaymentProjectionMapper.findByOrderId(orderId);
    }
    /** 保存原订单的唯一 Intent 关联。 */
    @Override
    public void insert(OrderPaymentProjection payment) {
        if (orderPaymentProjectionMapper.insert(payment) != 1) {
            throw new ErrorException("Intent 关联未成功保存");
        }
    }
}
