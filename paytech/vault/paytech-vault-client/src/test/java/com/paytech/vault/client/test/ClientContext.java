package com.paytech.vault.client.test;

import com.paytech.vault.client.autoconfigure.VaultClientAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public final class ClientContext {
    private ClientContext() {}
    public static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                RestClientAutoConfiguration.class, VaultClientAutoConfiguration.class))
                .withPropertyValues("spring.application.name=commerce-service", "vault.client.connect-timeout=1s",
                        "vault.client.read-timeout=1s");
    }
    public static ApplicationContextRunner direct(VaultSimulator simulator) {
        return runner().withPropertyValues("spring.profiles.active=test", "vault.client.base-url=" + simulator.url());
    }
}
