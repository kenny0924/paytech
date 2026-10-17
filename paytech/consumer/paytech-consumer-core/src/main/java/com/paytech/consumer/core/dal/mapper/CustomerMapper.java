package com.paytech.consumer.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.consumer.core.dal.dataobject.Customer;

/** Customer SQL 映射。 */
@Mapper
public interface CustomerMapper {
    /** 插入新 Customer；同一 Client 同一引用已有有效 Customer 时由唯一索引拒绝。 */
    int insert(@Param("customer") Customer customer);

    /** 按本库主键读取。 */
    Customer findById(@Param("customerId") long customerId);

    /** 按 Client 与公开编号读取，含已删除。 */
    Customer findByNo(@Param("clientId") long clientId, @Param("customerNo") String customerNo);

    /** 按 Client 与引用读取有效 Customer。 */
    Customer findActive(@Param("clientId") long clientId, @Param("consumerReference") String consumerReference);

    /** 版本条件下删除有效 Customer。 */
    int delete(@Param("customer") Customer customer, @Param("now") Instant now);
}
