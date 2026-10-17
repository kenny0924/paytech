package com.paytech.payment.execution.core.action.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.domain.PayContext;

/** 支付动作流程工厂 */
@Component
public class ActionFlowFactory {
    /** 按工具类型和入口操作注册的流程 */
    private final Map<String, ActionFlow<PayContext>> flows;

    /** 收集流程定义，同一标识只允许一个实现。 */
    public ActionFlowFactory(List<ActionFlow<PayContext>> implementations) {
        Map<String, ActionFlow<PayContext>> registered = new HashMap<>();
        for (ActionFlow<PayContext> flow : implementations) {
            if (registered.putIfAbsent(flow.flowKey(), flow) != null) {
                throw new IllegalArgumentException("支付流程存在重复定义：" + flow.flowKey());
            }
        }
        flows = Map.copyOf(registered);
    }

    /** 获取指定工具类型和入口操作的流程。 */
    public ActionFlow<PayContext> getFlow(String key) {
        ActionFlow<PayContext> flow = flows.get(key);
        if (flow == null) {
            throw new ErrorException("支付流程尚未实现：" + key);
        }
        return flow;
    }
}
