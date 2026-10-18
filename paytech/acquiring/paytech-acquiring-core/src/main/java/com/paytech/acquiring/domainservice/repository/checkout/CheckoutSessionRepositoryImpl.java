package com.paytech.acquiring.domainservice.repository.checkout;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.CheckoutVersionView;
import com.paytech.acquiring.core.dal.mapper.checkout.CheckoutSessionMapper;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 会话单表写入，由数据库唯一索引保护 Token 唯一性。 */
@Repository
@AllArgsConstructor
public class CheckoutSessionRepositoryImpl implements CheckoutSessionRepository {
    private final CheckoutSessionMapper checkoutSessionMapper;

    /** 只解析 Token 所属订单，业务状态在取得订单锁后读取。 */
    @Override
    public Long findOrderIdByPublicToken(String token) {
        return checkoutSessionMapper.findOrderIdByPublicToken(token);
    }

    /** 只读取 Token 对应的会话。 */
    @Override
    public CheckoutSession findByPublicToken(String token) {
        return checkoutSessionMapper.findByPublicToken(token);
    }

    /** 只重验身份与版本，不重新加载展示数据。 */
    @Override
    public CheckoutVersionView findQueryVersions(String token, long orderId, long sessionId) {
        return checkoutSessionMapper.findQueryVersions(token, orderId, sessionId);
    }

    /** 按订单查询会话表，不推导订单接入方式。 */
    @Override
    public CheckoutSession findByOrderId(long orderId) {
        return checkoutSessionMapper.findByOrderId(orderId);
    }

    /** 单次插入并核对影响行数，失败由调用方事务整体回滚。 */
    @Override
    public void insert(CheckoutSession session) {
        if (checkoutSessionMapper.insert(session) != 1) {
            throw new ErrorException("插入 Checkout Session 受影响行数异常");
        }
    }

    /** 条件更新成功后同步原取消时刻及会话版本；失败由完整用例事务回滚。 */
    @Override
    public void cancel(CheckoutSession session, Instant now) {
        if (checkoutSessionMapper.cancel(session, now) != 1) {
            throw new ConcurrentRequestException("取消时 Session 已改变");
        }
        session.setSessionStatus(CheckoutSessionStatus.CANCELLED);
        session.setCancelledAt(now);
        session.setVersion(session.getVersion() + 1);
        session.setUpdateTime(now);
    }

    /** 行数确认后同步完成时刻及会话版本；失败由完整用例事务回滚。 */
    @Override
    public void complete(CheckoutSession session, Instant completedAt, Instant now) {
        if (checkoutSessionMapper.complete(session, completedAt, now) != 1) {
            throw new ConcurrentRequestException("支付成功时 Session 已改变");
        }
        session.setSessionStatus(CheckoutSessionStatus.COMPLETED);
        session.setCompletedAt(completedAt);
        session.setVersion(session.getVersion() + 1);
        session.setUpdateTime(now);
    }
}
