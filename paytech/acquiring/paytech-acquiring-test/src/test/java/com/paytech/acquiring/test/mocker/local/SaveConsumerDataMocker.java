package com.paytech.commerce.test.mocker.local;

import java.util.function.Consumer;
import org.springframework.boot.test.context.TestComponent;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.test.dal.OrderCancellationTestMapper;
import com.paytech.commerce.test.dal.SaveConsumerTestMapper;
import lombok.AllArgsConstructor;

/** 只准备消费者保存所需的数据库边界，数据由已有全局 Mocker 按订单清理。 */
@TestComponent
@AllArgsConstructor
public class SaveConsumerDataMocker {
    private final SaveConsumerTestMapper mapper;
    private final OrderCancellationTestMapper orders;

    /** 从本例已提交订单设置状态、时刻或版本。 */
    public void changeOrder(long orderId, Consumer<CommerceOrder> change) {
        var order = orders.order(orderId);
        change.accept(order);
        mapper.changeOrder(order);
    }

    /** 准备不同会话生命周期。 */
    public void changeSession(long orderId, CheckoutSessionStatus status) {
        mapper.changeSession(orderId, status);
    }

    /** 为不存在分支准备孤立会话；全局清理仍按原订单 ID 删除关联事实。 */
    public void removeOrder(long orderId) {
        mapper.deleteOrder(orderId);
    }
}
