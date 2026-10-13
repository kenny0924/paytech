package com.paytech.platform.support.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.paytech.platform.support.test.mocker.external.SmtpMockServer;

/** 只扫描测试辅助组件，生产链由正式启动类装配；外部 HTTP Mock 的 Controller 由独立服务装配。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.paytech.platform.support.test.dal", sqlSessionTemplateRef = "sqlSessionTemplate")
@ComponentScan(basePackages = "com.paytech.platform.support.test", useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = TestComponent.class),
    excludeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, classes = TestConfiguration.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.web.bind.annotation.RestController.class)})
public class LocalTestConfiguration {

    /** SMTP 随机端口须在 JavaMailSender 创建前写入；其余连接配置保留在 application-mock.yml。 */
    @Bean
    DynamicPropertyRegistrar smtpMockServerProperties(SmtpMockServer smtp) {
        return registry -> registry.add("spring.mail.port", smtp::port);
    }
}
