package com.paytech.foundation.compensation;

import java.util.concurrent.atomic.AtomicLong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationRecoveryAuditRecorder;
import com.paytech.foundation.compensation.test.mapper.TestMapper;
import com.paytech.foundation.compensation.test.support.DatabaseLookupCompensationHandler;
import com.paytech.foundation.compensation.test.support.ExceptionClassificationCompensationHandler;
import com.paytech.foundation.compensation.test.support.TestCompensationRecoveryAuditRecorder;
import com.paytech.foundation.compensation.test.support.TestHandlerProbe;
import com.paytech.foundation.id.IdGenerator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 所有补偿测试共用的 Spring Boot 测试应用。
 *
 * @author Codex
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackageClasses = TestMapper.class)
public class TestApplication {

    @Bean
    IdGenerator testIdGenerator() {
        AtomicLong sequence = new AtomicLong(10_000L);
        return sequence::incrementAndGet;
    }

    @Bean
    MeterRegistry testMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    TestHandlerProbe testHandlerProbe() {
        return new TestHandlerProbe();
    }

    @Bean
    CompensationEventHandler databaseLookupCompensationHandler(TestMapper testMapper,
        TestHandlerProbe testHandlerProbe) {
        return new DatabaseLookupCompensationHandler(testMapper, testHandlerProbe);
    }

    @Bean
    CompensationEventHandler exceptionClassificationCompensationHandler(TestMapper testMapper) {
        return new ExceptionClassificationCompensationHandler(testMapper);
    }

    @Bean
    CompensationRecoveryAuditRecorder compensationRecoveryAuditRecorder(TestMapper testMapper) {
        return new TestCompensationRecoveryAuditRecorder(testMapper);
    }
}
