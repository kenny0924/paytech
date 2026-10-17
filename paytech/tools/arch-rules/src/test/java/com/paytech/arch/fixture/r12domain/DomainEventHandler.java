package com.paytech.arch.fixture.r12domain;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Fixture 版领域事件 Handler 注解，与 Foundation 注解同形。 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEventHandler {

    String eventType();

    boolean strictOrder();
}
