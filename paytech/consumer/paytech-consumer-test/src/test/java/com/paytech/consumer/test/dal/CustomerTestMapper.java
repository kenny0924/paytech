package com.paytech.commerce.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Customer 测试专用的检查、只读查询与清理；不替换生产 Mapper。 */
@Mapper
public interface CustomerTestMapper {
    /** 零行查询只检查 Customer 表与订单关联列存在，绝不创建或迁移 Schema。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_customer WHERE 1=0) + (SELECT COUNT(customer_id) FROM t_commerce_order WHERE 1=0) + (SELECT COUNT(*) FROM t_client_stored_card_setting WHERE 1=0)")
    int checkCustomerTables();

    /** 两个测试 Client 的全部 Customer，按创建先后排列。 */
    @Select("SELECT customer_id AS customerId, customer_no AS customerNo, client_id AS clientId, consumer_reference AS consumerReference, active_reference AS activeReference, customer_status AS status, deleted_time AS deletedTime FROM t_customer WHERE client_id IN (#{clientId},#{otherClientId}) ORDER BY create_time, customer_id")
    List<Map<String, Object>> customers(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    /** 订单关联的 Customer。 */
    @Select("SELECT customer_id FROM t_commerce_order WHERE client_id=#{clientId} AND client_order_id=#{clientOrderId}")
    Long orderCustomerId(@Param("clientId") long clientId, @Param("clientOrderId") String clientOrderId);


    /** 删除两个测试 Client 的 Customer 的失效任务。 */
    @Delete("""
        DELETE e FROM t_compensation_event e JOIN t_customer c ON CAST(c.customer_id AS CHAR)=e.event_key
        WHERE c.client_id IN (#{clientId},#{otherClientId}) AND e.event_type='STORED_CARD_INVALIDATION'
        """)
    int deleteTasks(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    /** 插入一个有效 Customer。 */
    @Insert("INSERT INTO t_customer (customer_id,customer_no,client_id,consumer_reference,active_reference,customer_status,deleted_time,version,create_time,update_time) VALUES (#{customerId},#{customerNo},#{clientId},#{reference},#{reference},'ACTIVE',NULL,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertCustomer(@Param("customerId") long customerId, @Param("customerNo") String customerNo,
        @Param("clientId") long clientId, @Param("reference") String reference);

    /** 把订单关联到 Customer。 */
    @Update("UPDATE t_commerce_order SET customer_id=#{customerId} WHERE order_id=#{orderId}")
    int linkOrder(@Param("orderId") long orderId, @Param("customerId") long customerId);

    /** 设置 Client 存卡开通。 */
    @Insert("INSERT INTO t_client_stored_card_setting (client_id,stored_card_enabled,version,create_time,update_time) VALUES (#{clientId},#{enabled},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE stored_card_enabled=#{enabled}")
    int setStoredCardEnabled(@Param("clientId") long clientId, @Param("enabled") boolean enabled);

    /** 删除两个测试 Client 的存卡开通。 */
    @Delete("DELETE FROM t_client_stored_card_setting WHERE client_id IN (#{clientId},#{otherClientId})")
    int deleteSettings(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    /** 删除两个测试 Client 的 Customer。 */
    @Delete("DELETE FROM t_customer WHERE client_id IN (#{clientId},#{otherClientId})")
    int deleteCustomers(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);
}
