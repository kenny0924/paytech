package com.paytech.acquiring.core.dal.dataobject.customer;

/** Session 与 Order 的联合查询投影，禁止跨 HTTP 暴露。 */
public record CustomerSessionView(
    /** 本库 Checkout Session 主键。 */
    long sessionId,
    /** Session 所属订单主键。 */
    long orderId,
    /** 订单归属 Client。 */
    long clientId,
    /** 本库订单的公开编号。 */
    String orderNo,
    /** Session 版本，用于外部查询后的本库快照重验。 */
    long version) {}
