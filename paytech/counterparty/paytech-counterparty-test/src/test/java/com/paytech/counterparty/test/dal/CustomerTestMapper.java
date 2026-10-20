package com.paytech.counterparty.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Customer 测试专用的检查、只读查询与清理；只触碰 mor_counterparty 的 t_customer 与本库 t_compensation_event，不替换生产 Mapper。 */
@Mapper
public interface CustomerTestMapper {
    /** 零行查询只检查本库两张表存在，绝不创建或迁移 Schema。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_customer WHERE 1=0) + (SELECT COUNT(*) FROM t_compensation_event WHERE 1=0)")
    int checkCustomerTables();

    /** 当前连接的库名，确认测试只连 mor_counterparty。 */
    @Select("SELECT DATABASE()")
    String databaseName();

    /** 两个测试 Client 的全部 Customer，按创建先后排列。 */
    @Select("SELECT customer_id AS customerId, customer_no AS customerNo, client_id AS clientId, consumer_reference AS consumerReference, active_reference AS activeReference, customer_status AS status, deleted_time AS deletedTime FROM t_customer WHERE client_id IN (#{clientId},#{otherClientId}) ORDER BY create_time, customer_id")
    List<Map<String, Object>> customers(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    /** 删除两个测试 Client 的 Customer 的失效任务（本库补偿表）。 */
    @Delete("""
        DELETE e FROM t_compensation_event e JOIN t_customer c ON CAST(c.customer_id AS CHAR)=e.event_key
        WHERE c.client_id IN (#{clientId},#{otherClientId}) AND e.event_type='STORED_CARD_INVALIDATION'
        """)
    int deleteTasks(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    /** 插入一个指定状态的 Customer，供资格与按主键读取场景准备事实。 */
    @Insert("INSERT INTO t_customer (customer_id,customer_no,client_id,consumer_reference,active_reference,customer_status,deleted_time,version,create_time,update_time) VALUES (#{customerId},#{customerNo},#{clientId},#{reference},IF(#{status}='ACTIVE',#{reference},NULL),#{status},IF(#{status}='ACTIVE',NULL,UTC_TIMESTAMP(6)),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertCustomer(@Param("customerId") long customerId, @Param("customerNo") String customerNo,
        @Param("clientId") long clientId, @Param("reference") String reference, @Param("status") String status);

    /** 删除两个测试 Client 的 Customer。 */
    @Delete("DELETE FROM t_customer WHERE client_id IN (#{clientId},#{otherClientId})")
    int deleteCustomers(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);
}
