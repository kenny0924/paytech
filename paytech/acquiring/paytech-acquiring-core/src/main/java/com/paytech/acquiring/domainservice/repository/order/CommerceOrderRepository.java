package com.paytech.acquiring.domainservice.repository.order;

import java.time.Instant;
import java.util.List;

import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;

/** CommerceOrder 的单表持久化边界。 */
public interface CommerceOrderRepository {
    /** 已通过 Checkout Token 定位订单身份后，读取订单本表。 */
    CommerceOrder findByOrderId(long orderId);

    /**
     * 条件保存消费者变更的订单版本；资料写入失败时由同一事务回滚
     * <li>quote 非空时同时把订单当前报价金额与金额状态改为该计算；已有消费者接受的订单不更新（SQL 条件保护）。</li>
     *
     * @param order 原订单，成功后同步版本与报价
     * @param session Token 绑定的会话
     * @param quote 本次重算的新计算；存量订单不重算时为 null
     * @param now 写入时刻
     */
    void recordConsumerChange(CommerceOrder order, CheckoutSession session, OrderTaxCalculation quote, Instant now);

    /** 受理支付时以当前版本保护订单、会话、合同与 Intent，成功后同步原订单。 */
    void recordPaymentSubmission(CommerceOrder order, CheckoutSession session, long contractId,
        OrderPaymentProjection payment, Instant now);

    /** 按可信 Client 和公开订单号查询本表，不附带会话或支付数据。 */
    CommerceOrder findByOrderNo(long clientId, String orderNo);

    /** 按可信 Client 和公开订单号只查询主键，供归属校验及关联查询使用。 */
    Long findOrderIdByOrderNo(long clientId, String orderNo);

    /** 按可信 Client 和订单主键只查询公开订单号，供关联资源展示使用。 */
    String findOrderNoByOrderId(long clientId, long orderId);

    /** 仅查询 Client 业务订单号占用的订单身份。 */
    Long findOrderId(long clientId, String clientOrderId);

    /** 插入一条记录，参与调用方本地事务。 */
    void insert(CommerceOrder value);

    /** 以版本 CAS 保存终止意图，成功后直接同步传入对象的时刻和版本。 */
    void requestTermination(CommerceOrder order, Instant now);

    /**
     * 按原订单及支付投影更新取消终态，与 Session 的原子提交由 Service 负责。
     * localOnly 为 true 时额外要求没有接受或历史准备；否则调用方已取得 Payment 的确定取消结果。
     */
    void cancel(CommerceOrder order, OrderPaymentProjection payment, boolean localOnly, Instant now);

    /** 以订单版本将 OPEN 订单推进为 PAID，成功后同步状态、支付时刻与版本。 */
    void markPaid(CommerceOrder order, Instant paidAt, Instant now);

    /**
     * 按订单主键游标与分片读取一页已到期、仍为 CREATED/OPEN 的订单主键，按主键升序
     *
     * @param now 判断到期的时刻
     * @param afterOrderId 上一页最后一个订单主键，首页为 0
     * @param shardIndex 分片序号，从 0 开始
     * @param shardTotal 分片总数
     * @param limit 本页最多条数
     * @return 订单主键，没有时为空列表
     */
    List<Long> findExpiredOrderIds(Instant now, long afterOrderId, int shardIndex, int shardTotal, int limit);

    /**
     * 按原订单及支付投影更新过期终态，与 Session、投影和 Webhook 的原子提交由 Service 负责。
     * localOnly 为 true 时额外要求没有接受证据；否则调用方已取得 Payment 的确定终止结果或 Intent 不存在。
     */
    void expire(CommerceOrder order, OrderPaymentProjection payment, boolean localOnly, Instant now);
}
