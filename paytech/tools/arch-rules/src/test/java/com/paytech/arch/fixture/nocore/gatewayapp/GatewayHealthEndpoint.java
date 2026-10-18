package com.paytech.arch.fixture.nocore.gatewayapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayHealthEndpoint {

    @GetMapping("/gateway/v1/ping")
    public String ping() {
        return "pong";
    }
}
