package com.paytech.foundation.messaging.core.topology;

import java.util.List;
import java.util.Objects;

/**
 * 恢复路径中的一项声明：交换机、队列或绑定；依赖项未就绪时本项暂缓，与依赖项一起等待重试。
 *
 * @param item 本单元内唯一的声明项描述，例如 {@code queue ledger.billing.p0}
 * @param action 声明动作，失败抛出 {@link org.springframework.amqp.AmqpException}
 * @param dependsOn 依赖的声明项描述
 * @author Claude
 */
record TopologyDeclaration(String item, Runnable action, List<String> dependsOn) {

    TopologyDeclaration {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(action, "action must not be null");
        dependsOn = List.copyOf(dependsOn);
    }

    /**
     * 创建声明项。
     *
     * @param item 声明项描述
     * @param action 声明动作
     * @param dependsOn 依赖的声明项描述
     * @return 声明项
     */
    static TopologyDeclaration of(String item, Runnable action, String... dependsOn) {
        return new TopologyDeclaration(item, action, List.of(dependsOn));
    }
}
