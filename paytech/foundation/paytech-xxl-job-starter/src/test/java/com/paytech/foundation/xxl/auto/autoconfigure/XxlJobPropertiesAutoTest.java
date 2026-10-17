package com.paytech.foundation.xxl.auto.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.xxl.core.autoconfigure.XxlJobProperties;
import com.paytech.foundation.xxl.core.error.XxlJobErrorCode;

class XxlJobPropertiesAutoTest {

    @Test
    void acceptsCompleteEnabledConfiguration() {
        XxlJobProperties properties = validProperties();

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    void allowsCallerOwnedJobHandlerPackagesToBeExcluded() {
        XxlJobProperties properties = validProperties();
        properties.getExecutor().setExcludedPackages("com.example.caller.jobs.");

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    void rejectsWeakAccessToken() {
        XxlJobProperties properties = validProperties();
        properties.getAdmin().setAccessToken("weak-token");

        FailException exception = assertThrows(FailException.class, properties::validateEnabledConfiguration);

        assertEquals(XxlJobErrorCode.CONFIGURATION_INVALID.getCode(), exception.getCode());
        assertInstanceOf(FailException.class, exception);
    }

    @Test
    void rejectsImplicitExecutorPort() {
        XxlJobProperties properties = validProperties();
        properties.getExecutor().setPort(0);

        assertThrows(FailException.class, properties::validateEnabledConfiguration);
    }

    private XxlJobProperties validProperties() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.getAdmin().setAddresses("http://127.0.0.1:18090");
        properties.getAdmin().setAccessToken("local-xxl-job-access-token-000001");
        properties.getExecutor().setAppName("payment-service");
        properties.getExecutor().setPort(19999);
        return properties;
    }
}
