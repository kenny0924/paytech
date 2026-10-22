package com.paytech.acquiring.domainservice.repository.checkout;

import java.time.Instant;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.CheckoutVersionView;

/** Checkout Session 单表持久化边界。 */
public interface CheckoutSessionRepository {
    /** 仅解析锁资源所需的稳定订单主键，不加载业务状态。 */
    Long findOrderIdByPublicToken(String token);

    /** 按公开 Token 读取实际会话，不附带其他用例数据。 */
    CheckoutSession findByPublicToken(String token);

    /** 返回前重验原 Token、Session 绑定及订单、消费者版本。 */
    CheckoutVersionView findQueryVersions(String token, long orderId, long sessionId);

    /** 查询实际关联的会话；订单未关联会话时返回空。 */
    CheckoutSession findByOrderId(long orderId);

    /** 插入会话，唯一键冲突由调用方事务整体回滚。 */
    void insert(CheckoutSession session);

    /** 以 Session 版本 CAS 关闭会话，不修改 Order；参与调用方本地事务。 */
    void cancel(CheckoutSession session, Instant now);

    /** 以 Session 版本将未终结会话推进为 EXPIRED，不修改 Order；参与调用方本地事务。 */
    void expire(CheckoutSession session, Instant now);

    /** 以 Session 版本将未终结会话推进为 COMPLETED；参与调用方本地事务。 */
    void complete(CheckoutSession session, Instant completedAt, Instant now);
}
