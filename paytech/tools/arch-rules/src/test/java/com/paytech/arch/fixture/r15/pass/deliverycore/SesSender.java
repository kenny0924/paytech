package com.paytech.arch.fixture.r15.pass.deliverycore;

import software.amazon.awssdk.fixture.SdkClientStub;

public class SesSender {

    public void deliver(SdkClientStub client) {
        client.call();
    }
}
