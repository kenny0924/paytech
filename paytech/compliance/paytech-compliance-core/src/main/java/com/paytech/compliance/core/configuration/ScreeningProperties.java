package com.paytech.compliance.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** 名单筛查配置（compliance.screening.*）。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "compliance.screening")
public class ScreeningProperties {
    /**
     * 执行 SCREENING_SEARCH 的 channel-connector 服务渠道编码
     * <li>缺省 S_SCREENING_MOCK（仅 local / mor-test 部署该 Mock 渠道）；生产须改为真实供应商渠道，未配置渠道时筛查失败、案件不能批准。</li>
     * <li>每次执行时读取。</li>
     */
    private String channelCode = "S_SCREENING_MOCK";
}
