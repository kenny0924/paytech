package com.paytech.consumer.domainservice.repository;

import java.time.Instant;

import com.paytech.consumer.core.dal.dataobject.Customer;

/** Customer 数据访问。 */
public interface CustomerRepository {
    /** 插入新 Customer；同一引用已有有效 Customer 时抛出 {@link org.springframework.dao.DuplicateKeyException}。 */
    void insert(Customer customer);

    /** 按本库主键读取；不存在时返回 null。 */
    Customer findById(long customerId);

    /** 按 Client 与公开编号读取，含已删除；不存在或跨 Client 时返回 null。 */
    Customer findByNo(long clientId, String customerNo);

    /** 按 Client 与引用读取有效 Customer；没有时返回 null。 */
    Customer findActive(long clientId, String consumerReference);

    /** 版本条件下删除有效 Customer，成功后同步对象；条件不满足时抛出系统错误。 */
    void delete(Customer customer, Instant now);
}
