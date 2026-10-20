package com.paytech.counterparty.domainservice.service;

import java.time.Instant;

import com.paytech.counterparty.core.dal.dataobject.Customer;

/**
 * Customer 本地能力：只操作 counterparty 库（mor_counterparty），不调用外部服务。
 * <li>Customer 的身份只来自 Client 服务端传入的 consumerReference；同一 Client 同一引用同时最多一个有效 Customer。</li>
 */
public interface CustomerService {
    /**
     * 取得引用对应的有效 Customer，没有则新建；并发新建撞唯一键时返回获胜的那条
     *
     * @param clientId 可信 Client 主键
     * @param consumerReference 已校验的消费者引用
     * @param now 本次处理时间
     * @return Customer 与是否本次新建
     */
    Resolution resolveActiveCustomer(long clientId, String consumerReference, Instant now);

    /**
     * 删除有效 Customer，并在同一事务登记“失效其已存卡”的补偿任务；任务在提交后调用 Payment
     *
     * @param customer 已核对归属的有效 Customer
     * @param now 删除时间
     */
    void deleteCustomer(Customer customer, Instant now);

    /**
     * 查或建的结果
     *
     * @param customer 有效 Customer
     * @param created 是否本次新建
     */
    record Resolution(Customer customer, boolean created) {}
}
