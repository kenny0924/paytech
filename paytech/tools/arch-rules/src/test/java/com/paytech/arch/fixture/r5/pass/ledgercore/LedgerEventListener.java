package com.paytech.arch.fixture.r5.pass.ledgercore;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class LedgerEventListener {

    @RabbitListener(queues = "cg_ledger_economic_fact")
    public void onEconomicFact(String payload) {
    }
}
