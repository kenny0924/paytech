package com.paytech.acquiring.domainservice.repository.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.core.dal.mapper.order.OrderPaymentProjectionMapper;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

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

    /** 行数确认后同步投影状态、成功尝试、金额、事件幂等事实与版本。 */
    @Override
    public void recordSucceeded(OrderPaymentProjection payment, long attemptId, BigDecimal capturedAmount,
        String eventKey, String payloadHash, long sourceVersion, Instant eventTime, Instant now) {
        if (orderPaymentProjectionMapper.recordSucceeded(payment, attemptId, capturedAmount, eventKey, payloadHash,
            sourceVersion, eventTime, now) != 1) {
            throw new ConcurrentRequestException("订单支付投影已改变");
        }
        payment.setIntentStatus(PaymentIntentStatus.SUCCEEDED);
        payment.setSuccessfulAttemptId(attemptId);
        payment.setCapturedAmount(capturedAmount);
        payment.setLastAttemptEventKey(eventKey);
        payment.setLastAttemptPayloadHash(payloadHash);
        payment.setVersion(payment.getVersion() + 1);
    }
}
