package com.paytech.platform.support.auditsecurity.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;

/** 审计专用资源预算；只能缩小队列，不能通过配置扩张已批准的资源上限。 */
@Getter
@Validated
@ConfigurationProperties("operations.audit")
public class OperationsAuditProperties {
    @Min(1)
    @Max(256)
    private int queueCapacity = 256;
    private final int workerCount = 1;
    private final int maximumPoolSize = 1;
    private final int minimumIdle = 0;
    private final int maxEventBytes = 65536;
    private final int maxAttributes = 256;
    private final int maxAttributeBytes = 4096;
    private final int maxAttributeCodePoints = 1000;
    private final int connectionTimeoutMillis = 1000;
    private final int validationTimeoutMillis = 500;
    private final int connectTimeoutMillis = 1000;
    private final int lockWaitTimeoutSeconds = 1;
    private final int statementTimeoutSeconds = 2;
    private final int transactionTimeoutSeconds = 3;
    private final int socketTimeoutMillis = 3000;
    private final int shutdownTimeoutMillis = 5000;

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
