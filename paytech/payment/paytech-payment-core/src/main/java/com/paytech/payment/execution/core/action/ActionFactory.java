package com.paytech.payment.execution.core.action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;

/** 支付动作实现工厂 */
@Component
public class ActionFactory {
    /** 按动作代码注册的实现 */
    private final Map<String, ActionActor> actions;

    /** 收集动作实现，同一代码只允许一个实现。 */
    public ActionFactory(List<ActionActor> implementations) {
        Map<String, ActionActor> registered = new HashMap<>();
        for (ActionActor actionActor : implementations) {
            if (registered.putIfAbsent(actionActor.getCode(), actionActor) != null) {
                throw new IllegalArgumentException("支付动作存在重复实现：" + actionActor.getCode());
            }
        }
        actions = Map.copyOf(registered);
    }

    /** 获取指定动作的执行实现。 */
    public <T extends ActionContext> ActionActor getAction(ActionType type) {
        ActionActor actionActor = actions.get(type.getCode());
        if (actionActor == null) {
            throw new ErrorException("支付动作尚未实现：" + type.getCode());
        }
        return actionActor;
    }
}