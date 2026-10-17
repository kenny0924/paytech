package com.paytech.commerce.test.checker.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commerce.test.checker.order.OrderQueryChecker;
import com.paytech.commerce.test.dal.RefundCreationTestMapper;

import lombok.AllArgsConstructor;

/** 退款查询的数据库只读检查，包含本测试两个 Client 的所有订单、退款和任务。 */
@TestComponent
@AllArgsConstructor
public class RefundQueryChecker {
    private final OrderQueryChecker orders;
    private final RefundCreationTestMapper refunds;

    /** 全列快照只用于检查副作用，不用查询结果生成响应期望。 */
    public Map<Long, List<List<Map<String, Object>>>> snapshot(long clientId, long otherClientId) {
        var snapshot = orders.snapshot(clientId, otherClientId);
        for (long orderId : refunds.refundOrderIds(clientId, otherClientId)) {
            snapshot.putIfAbsent(orderId, List.of());
        }
        snapshot.replaceAll((orderId, rows) -> {
            var all = new ArrayList<>(rows);
            all.add(refunds.refundRows(orderId));
            all.add(refunds.taskRows(orderId));
            return all;
        });
        return snapshot;
    }

    public void assertUnchanged(Map<Long, List<List<Map<String, Object>>>> before, long clientId, long otherClientId) {
        assertEquals(before, snapshot(clientId, otherClientId), "查询不得修改订单、退款或提交任务");
    }
}
