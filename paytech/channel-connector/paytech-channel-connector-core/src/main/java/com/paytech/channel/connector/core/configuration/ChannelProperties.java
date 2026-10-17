package com.paytech.channel.connector.core.configuration;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/** channel-connector 所属的开放能力代码目录（channel-connector.capability-definitions），未知代码失败关闭。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("channel-connector")
public class ChannelProperties {
    /** 注册代码到类型和 CODE 值域的映射，部署时显式配置。 */
    @NotEmpty
    private Map<String, @Valid CapabilityDefinition> capabilityDefinitions;
}
