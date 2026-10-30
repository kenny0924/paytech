package com.paytech.risk.control.core.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/** 限额档位配置（REQ-04）。 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "risk.limit-tier")
public class LimitTierProperties {
    /**
     * 允许调用存量迁移内部接口（L12）的私网服务名（X-Caller-Service）
     * <li>默认空，即迁移入口关闭；只在发布迁移步骤期间临时配置，完成后清空。</li>
     * <li>每次请求读取当前值。</li>
     */
    private List<String> migrationCallers = new ArrayList<>();
}
