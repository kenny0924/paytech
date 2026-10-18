package com.paytech.foundation.compensation.core.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 补偿事件的有界执行配置。
 *
 * @author Codex
 */
@ConfigurationProperties(prefix = "compensation", ignoreUnknownFields = false)
public class CompensationEventProperties {

    private final CoordinatorPool coordinator = new CoordinatorPool();
    private final HandlerPool handler = new HandlerPool();
    private boolean enabled = true;
    private String instanceId;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private int scannerBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public int getScannerBatchSize() {
        return scannerBatchSize;
    }

    public void setScannerBatchSize(int scannerBatchSize) {
        this.scannerBatchSize = scannerBatchSize;
    }

    public CoordinatorPool getCoordinator() {
        return coordinator;
    }

    public HandlerPool getHandler() {
        return handler;
    }

    /** Dispatcher 协调线程池配置。 */
    public static class CoordinatorPool {

        private int coreSize = 10;
        private int maximumSize = 20;
        private int queueCapacity = 80;
        private Duration keepAlive = Duration.ofSeconds(60);

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(int maximumSize) {
            this.maximumSize = maximumSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public Duration getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(Duration keepAlive) {
            this.keepAlive = keepAlive;
        }
    }

    /** Handler 隔离线程池配置。 */
    public static class HandlerPool {

        private int maximumConcurrency = 20;

        public int getMaximumConcurrency() {
            return maximumConcurrency;
        }

        public void setMaximumConcurrency(int maximumConcurrency) {
            this.maximumConcurrency = maximumConcurrency;
        }
    }
}
