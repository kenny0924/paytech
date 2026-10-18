package com.paytech.platform.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;

/**
 * Platform Support service entry point.
 */
@SpringBootApplication(exclude = TransactionAutoConfiguration.class)
public class PlatformSupportApplication {

    /**
     * Starts the Platform Support application.
     *
     * @param args
     *            command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformSupportApplication.class, args);
    }
}
