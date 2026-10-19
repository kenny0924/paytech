package com.paytech.arch.fixture.r14.pass.ledgercore.service.processor;

import com.paytech.arch.fixture.r14.AbstractProcessor;

/** 直接继承同步命令模板。 */
public class PostJournalProcessor extends AbstractProcessor<String, String> {

    @Override
    protected String processRequest(String request) {
        return request;
    }
}
