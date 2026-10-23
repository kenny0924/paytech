package com.paytech.acquiring.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** ORDER_PAYMENT_SUCCEEDED 补偿任务的只读检查与按订单清理；订单数据由 CheckoutDataMocker 统一清理。 */
@Mapper
public interface OrderPaymentEventTestMapper {
    /** 该订单的发布任务（任意版本）；发布成功后删除。 */
    @Select("""
        SELECT event_key,event_type,sub_event_type,payload_json,execute_status,last_error_code
        FROM t_compensation_event
        WHERE event_type='ORDER_PAYMENT_SUCCEEDED' AND event_key LIKE CONCAT('ORDER:',#{orderId},':%')
        """)
    List<Map<String, Object>> tasks(long orderId);

    /** 删除该订单的发布任务，覆盖终态失败后保留的记录。 */
    @Delete("""
        DELETE FROM t_compensation_event
        WHERE event_type='ORDER_PAYMENT_SUCCEEDED' AND event_key LIKE CONCAT('ORDER:',#{orderId},':%')
        """)
    void deleteTasks(long orderId);
}
