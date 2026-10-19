package com.paytech.arch.fixture.r14.pass.ledgercore.service.processor;

import com.paytech.arch.fixture.r14.AbstractLockProcessor;

/** 经资源锁模板间接继承。 */
public class ReverseJournalProcessor extends AbstractLockProcessor<String, String> {

    @Override
    protected String processWithinLock(String request) {
        return request;
    }
}
