package com.paytech.acquiring.integration.consumer;

/**
 * consumer 内部合同端口：只在数据库事务外调用。
 * <li>依赖不可用时抛出 {@code ServiceUnavailableException}，协议不符时抛出上游协议异常。</li>
 */
public interface ConsumerCustomerClient {
    /**
     * 取得 Client 下该引用的有效 Customer，没有则由 consumer 新建（建或取）
     *
     * @param clientId 可信 Client 主键
     * @param consumerReference 已校验的不透明消费者引用
     * @return Customer 主键
     */
    long resolveCustomerId(long clientId, String consumerReference);

    /**
     * 读取 Customer 的公开编号，含已删除的 Customer
     *
     * @param customerId Customer 主键
     * @return 公开编号；consumer 返回不存在时为 null
     */
    String customerNo(long customerId);

    /**
     * 读取存卡资格；consumer 组合 Customer 状态与 Client 已存卡开关
     *
     * @param clientId 订单所属 Client
     * @param customerId 订单关联的 Customer
     * @return 资格结论
     */
    CardSavingDecision cardSavingEligibility(long clientId, long customerId);

    /**
     * 存卡资格结论
     *
     * @param eligible 是否可保存卡或使用已存卡
     * @param reason 不可时的原因代码；可用时为空
     * @param customerNo Customer 存在时的公开编号
     */
    record CardSavingDecision(boolean eligible, String reason, String customerNo) {}
}
