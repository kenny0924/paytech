package com.paytech.commerce.test.dal;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;

/** 消费者测试独立查询及竞争事实；所有写入限于当前测试登记的订单。 */
@Mapper
public interface SaveConsumerTestMapper {
    /** 独立查询本库消费者，复用数据库对象映射而非被测 Repository。 */
    @Select("SELECT * FROM t_consumer_context WHERE order_id=#{orderId}")
    @ResultMap("com.paytech.commerce.core.dal.mapper.customer.ConsumerContextMapper.ConsumerContext")
    ConsumerContext consumer(long orderId);

    /** 原始快照同时覆盖未映射的可选字段，失败不得产生部分修改。 */
    @Select("SELECT * FROM t_consumer_context WHERE order_id=#{orderId}")
    List<Map<String, Object>> consumerRows(long orderId);

    /** 资料保存不能改写法律事实。 */
    @Select("SELECT l.* FROM t_order_legal l JOIN t_order_context c ON c.order_context_id=l.order_context_id WHERE c.order_id=#{orderId}")
    List<Map<String, Object>> legalRows(long orderId);

    /** 保存资料不能重新生成合同。 */
    @Select("SELECT * FROM t_consumer_contract WHERE order_id=#{orderId}")
    List<Map<String, Object>> contractRows(long orderId);

    /** 准备状态、时间和版本边界，不调用任何被测业务。 */
    @Update("""
        UPDATE t_commerce_order SET order_status=#{orderStatus},expires_at=#{expiresAt},paid_at=#{paidAt},
          preparation_command_id=#{preparationCommandId},termination_requested_at=#{terminationRequestedAt},version=#{version}
        WHERE order_id=#{orderId}
        """)
    void changeOrder(CommerceOrder order);

    /** 只改变测试订单的会话状态。 */
    @Update("UPDATE t_checkout_session SET session_status=#{status} WHERE order_id=#{orderId}")
    void changeSession(@Param("orderId") long orderId, @Param("status") CheckoutSessionStatus status);

    /** 构造会话仍存在而订单缺失的异常关联，不操作其他测试数据。 */
    @Delete("DELETE FROM t_commerce_order WHERE order_id=#{orderId}")
    void deleteOrder(long orderId);
}
