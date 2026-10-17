package com.paytech.risk.compliance.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/** Client 入驻材料目录配置。 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "onboarding.materials")
public class MaterialRequirementsProperties {
    /**
     * 资格类型到材料程序的映射
     * <li>JSON 数组，每行严格为 {qualificationType, programCode}，每类资格最多一行，不超过 65536 字符。</li>
     * <li>缺省为空；为空、结构非法或无匹配时查询返回 503，不默认无需材料。</li>
     */
    private String programsJson = "";
}
