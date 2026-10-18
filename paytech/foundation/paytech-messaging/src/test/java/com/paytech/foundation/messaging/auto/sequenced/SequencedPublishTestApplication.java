package com.paytech.foundation.messaging.auto.sequenced;

import java.util.concurrent.atomic.AtomicLong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.id.IdGenerator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 发布侧串行测试应用：真实补偿组件（Dispatcher、扫描）+ 真实 messaging 发布端与 {@code AggregateKeySequencer}；不启用消费端。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackageClasses = SequencedCompensationMapper.class)
@Import({SequencedPublishHandler.class, SequencedPublishProbe.class})
public class SequencedPublishTestApplication {

    @Bean
    IdGenerator sequencedTestIdGenerator() {
        AtomicLong sequence = new AtomicLong(System.currentTimeMillis());
        return sequence::incrementAndGet;
    }

    @Bean
    MeterRegistry sequencedTestMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
