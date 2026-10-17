package com.paytech.foundation.messaging.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 {@link DomainEventConsumer} 消费的事件类型与顺序要求；事件类型必须登记在事件目录中（ArchUnit R12）。
 *
 * @author Claude
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEventHandler {

    /**
     * 消费的事件类型。
     *
     * @return PascalCase 事件类型，与事件目录一致
     */
    String eventType();

    /**
     * 是否要求同一聚合严格按版本连续消费。
     * <li>true：版本出现缺口时拒绝并重新投递，等待缺口补齐，超过投递上限进入 DLQ。</li>
     * <li>false：只跳过重放与旧版本，缺口直接处理。</li>
     *
     * @return 是否严格有序，须与事件目录的 strictOrder 一致
     */
    boolean strictOrder();
}
