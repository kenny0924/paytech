package com.paytech.arch.fixture.r15.pass.protocolcore;

import java.net.http.HttpClient;
import java.time.Duration;

public class ExecutionPlanner {

    public HttpClient transport() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }
}
