package com.paytech.arch.fixture.r7.violation.ledgercore;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class LedgerPublishService {

    private final RabbitTemplate rabbitTemplate;

    public LedgerPublishService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
}
