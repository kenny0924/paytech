package com.paytech.acquiring.test.mocker.local;

import java.time.Duration;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.configuration.OrderExpiryProperties;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * 过期扫描配置的用例级覆盖：直接调整生产配置对象，验证 Handler 每次执行时读取的批大小、页数与告警阈值。
 * <li>构造时记下 application-mock.yml 绑定的值，每例结束由公共清理恢复，不影响后续用例。</li>
 */
@TestComponent
public class OrderExpiryConfigMocker implements Clear {
    private final OrderExpiryProperties properties;
    private final int batchSize;
    private final int maxBatches;
    private final Duration pendingAlertAfter;

    /** 记录测试配置绑定的原始值。 */
    public OrderExpiryConfigMocker(OrderExpiryProperties properties) {
        this.properties = properties;
        this.batchSize = properties.getBatchSize();
        this.maxBatches = properties.getMaxBatches();
        this.pendingAlertAfter = properties.getPendingAlertAfter();
    }

    /** 本例使用指定的每页订单数与最多页数。 */
    public void mockPaging(int batchSize, int maxBatches) {
        properties.setBatchSize(batchSize);
        properties.setMaxBatches(maxBatches);
    }

    /** 本例使用指定的 PENDING 告警阈值。 */
    public void mockPendingAlertAfter(Duration threshold) {
        properties.setPendingAlertAfter(threshold);
    }

    /** 当前生效的 PENDING 告警阈值，供直接调用收尾 Processor 的用例使用。 */
    public Duration pendingAlertAfter() {
        return properties.getPendingAlertAfter();
    }

    /** 恢复测试配置绑定的原始值。 */
    @Override
    public void clear() {
        properties.setBatchSize(batchSize);
        properties.setMaxBatches(maxBatches);
        properties.setPendingAlertAfter(pendingAlertAfter);
    }
}
