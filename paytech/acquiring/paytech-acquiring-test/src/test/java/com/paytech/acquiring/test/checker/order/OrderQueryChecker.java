package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.test.dal.CheckoutCreationTestMapper;
import com.paytech.acquiring.test.dal.OrderQueryTestMapper;

import lombok.AllArgsConstructor;

/** 比较本次测试命名空间内全部真实数据库事实；不通过生产查询接口反推预期。 */
@TestComponent
@AllArgsConstructor
public class OrderQueryChecker {
    private final CheckoutCreationTestMapper roots;
    private final OrderQueryTestMapper mapper;

    /** 查询前记录全列状态，只用于验证无写入，不用它生成响应的业务期望。 */
    public Map<Long, List<List<Map<String, Object>>>> snapshot(long clientId, long otherClientId) {
        var snapshot = new LinkedHashMap<Long, List<List<Map<String, Object>>>>();
        for (long id : roots.orderIds(clientId, otherClientId)) {
            var order = mapper.orderRows(id);
            assertEquals(1, order.size(), "查询前置订单必须真实存在");
            snapshot.put(id, List.of(order, mapper.sessionRows(id), mapper.itemRows(id), mapper.paymentRows(id),
                mapper.refundRows(id), mapper.fundsRows(id), mapper.contextRows(id)));
        }
        return snapshot;
    }

    /** 成功或拒绝均不能改版本、金额、期限、投影，也不能创建额外订单。 */
    public void assertUnchanged(Map<Long, List<List<Map<String, Object>>>> before, long clientId, long otherClientId) {
        assertEquals(before, snapshot(clientId, otherClientId), "GET 不应修改本地订单及关联事实");
    }
}
