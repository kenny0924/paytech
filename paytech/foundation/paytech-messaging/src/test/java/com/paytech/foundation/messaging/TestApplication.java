package com.paytech.foundation.messaging;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.messaging.api.EventCatalog;
import com.paytech.foundation.messaging.catalog.YamlEventCatalog;
import com.paytech.foundation.messaging.test.mapper.TestMapper;
import com.paytech.foundation.messaging.test.support.BrokerTestSupport;
import com.paytech.foundation.messaging.test.support.BrokerTopologyProbe;
import com.paytech.foundation.messaging.test.support.DatabaseCleaner;
import com.paytech.foundation.messaging.test.support.HandledEventProbe;
import com.paytech.foundation.messaging.test.support.LooseNoteHandler;
import com.paytech.foundation.messaging.test.support.ManagedEntryHandler;
import com.paytech.foundation.messaging.test.support.MeterRegistryCleaner;
import com.paytech.foundation.messaging.test.support.StrictFactHandler;
import com.paytech.foundation.messaging.test.support.TestRun;
import com.paytech.foundation.messaging.test.support.TopologyErrorLogProbe;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * messaging 测试应用：真实自动配置、本地 MySQL 与 RabbitMQ；事件目录换成本次运行前缀下的测试服务名。
 * <li>与消费方测试相同，注册 {@link PartitionQueueBaselineConfiguration}，监听容器启动前清空本次运行的分区队列。</li>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackageClasses = TestMapper.class)
@Import({HandledEventProbe.class, StrictFactHandler.class, LooseNoteHandler.class, ManagedEntryHandler.class, BrokerTestSupport.class,
    BrokerTopologyProbe.class, TopologyErrorLogProbe.class, DatabaseCleaner.class, MeterRegistryCleaner.class,
    PartitionQueueBaselineConfiguration.class})
public class TestApplication {

    @Bean
    EventCatalog eventCatalog() {
        String yaml = """
            events:
              - eventType: %1$s
                schemaVersion: 1
                producer: %4$s
                aggregateType: TEST_FACT
                aggregateVersionField: factVersion
                consumers: [%5$s]
                strictOrder: true
                payloadSchemaRef: test/%1$s/v1
              - eventType: %2$s
                schemaVersion: 1
                producer: %4$s
                aggregateType: TEST_NOTE
                aggregateVersionField: noteVersion
                consumers: [%5$s]
                strictOrder: false
                payloadSchemaRef: test/%2$s/v1
              - eventType: %6$s
                schemaVersion: 1
                producer: %4$s
                aggregateType: TEST_ENTRY
                aggregateVersionField: entryVersion
                consumers: [%5$s]
                strictOrder: false
                payloadSchemaRef: test/%6$s/v1
              - eventType: %3$s
                schemaVersion: 1
                producer: %4$s
                aggregateType: TEST_AUDIT
                aggregateVersionField: auditVersion
                consumers: []
                strictOrder: false
                payloadSchemaRef: test/%3$s/v1
            """.formatted(TestRun.STRICT_EVENT, TestRun.LOOSE_EVENT, TestRun.UNSUBSCRIBED_EVENT, TestRun.PRODUCER,
            TestRun.CONSUMER, TestRun.MANAGED_EVENT);
        return YamlEventCatalog.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
            TestRun.SERVICES);
    }

    @Bean
    MeterRegistry testMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
